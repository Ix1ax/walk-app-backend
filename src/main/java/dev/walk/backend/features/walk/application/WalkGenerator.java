package dev.walk.backend.features.walk.application;

import dev.walk.backend.common.exception.NotFoundException;
import dev.walk.backend.features.city.application.CityService;
import dev.walk.backend.features.city.domain.City;
import dev.walk.backend.features.geo.GeoapifyClient;
import dev.walk.backend.features.geo.domain.GeoDistance;
import dev.walk.backend.features.geo.domain.GeoPoint;
import dev.walk.backend.features.geo.domain.GeoRoute;
import dev.walk.backend.features.geo.domain.RouteGeometry;
import dev.walk.backend.features.geo.domain.RouteLeg;
import dev.walk.backend.features.place.application.PlaceEnricher;
import dev.walk.backend.features.place.application.PlaceService;
import dev.walk.backend.features.place.domain.Place;
import dev.walk.backend.features.place.domain.PlaceMedia;
import dev.walk.backend.features.walk.domain.Walk;
import dev.walk.backend.features.walk.domain.WalkPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author Ilya Samsonov
 * Генерация пешей прогулки от стартовой точки. Берёт пул мест рядом, отбирает
 * до 6 разнообразных точек под бюджет длительности, оптимизирует порядок обхода
 * (кольцо), строит реальный пеший маршрут и оценивает время. Ничего не сохраняет
 * — это превью
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalkGenerator {

    /**
     * Желаемый максимум точек в прогулке
     */
    private static final int MAX_POINTS = 6;
    /**
     * Минимум, ниже которого прогулку не строим (нечего показывать)
     */
    private static final int MIN_POINTS = 2;
    /**
     * Сколько верхних кандидатов из ранжированного списка просматриваем при подборе
     * маршрута. Этого достаточно, чтобы пропускать неудачные дальние точки, но не
     * превращать генерацию превью в дорогой перебор всего пула
     */
    private static final int CANDIDATE_SCAN_LIMIT = 40;
    private final PlaceService placeService;
    private final NearbyPlacesSelector selector;
    private final RouteOptimizer optimizer;
    private final WalkTimeEstimator estimator;
    private final PlaceEnricher enricher;
    private final CityService cityService;
    private final GeoapifyClient geoapifyClient;

    public Walk generate(double lat, double lon, int durationMinutes, boolean returnToStart) {
        int searchRadius = searchRadius(durationMinutes);
        List<Place> pool = placeService.poolNearby(lat, lon, searchRadius);
        if (pool.size() < MIN_POINTS) {
            throw new NotFoundException("Недостаточно мест рядом, чтобы построить прогулку");
        }

        List<Place> ranked = selector.rank(lat, lon, pool);
        List<Place> route = chooseRoute(lat, lon, ranked, durationMinutes, returnToStart);
        Walk walk = build(lat, lon, route, returnToStart);

        log.info("Прогулка: старт=({},{}), бюджет={}мин, {} → {} точек, {}м, {}мин",
                lat, lon, durationMinutes, returnToStart ? "кольцо" : "открытый путь",
                walk.points().size(), walk.totalDistanceMeters(), walk.totalMinutes());
        return walk;
    }

    /**
     * Подбирает точки под бюджет времени. Список {@code ranked} уже отсортирован по
     * разнообразию и близости, но отдельная ранняя точка может оказаться слишком
     * далёкой или долгой. Поэтому не берём простой префикс: просматриваем верхние
     * кандидаты и добавляем только те, с которыми маршрут остаётся в бюджете.
     * Если даже две точки не помещаются — отдаём самый короткий маршрут из пары
     */
    private List<Place> chooseRoute(double lat, double lon, List<Place> ranked,
                                    int durationMinutes, boolean returnToStart) {
        double budget = durationMinutes;

        int scan = Math.min(CANDIDATE_SCAN_LIMIT, ranked.size());
        List<Place> selected = new ArrayList<>(MAX_POINTS);
        List<Place> best = List.of();

        for (Place candidate : ranked.subList(0, scan)) {
            if (selected.size() == MAX_POINTS) {
                break;
            }
            List<Place> attempt = new ArrayList<>(selected);
            attempt.add(candidate);
            List<Place> ordered = optimizer.order(lat, lon, attempt, returnToStart);
            double minutes = estimateRouteMinutes(lat, lon, ordered, returnToStart);
            if (minutes <= budget) {
                selected = new ArrayList<>(ordered);
                if (selected.size() >= MIN_POINTS) {
                    best = selected;
                }
            }
        }

        if (best.size() >= MIN_POINTS) {
            return best;
        }
        return shortestFallback(lat, lon, ranked.subList(0, scan), returnToStart);
    }

    /**
     * Fallback для короткого бюджета или скудного пула: находим самую короткую пару
     * среди просмотренных кандидатов, а не просто берём первые две ранжированные точки
     */
    private List<Place> shortestFallback(double lat, double lon, List<Place> candidates, boolean returnToStart) {
        if (candidates.size() <= MIN_POINTS) {
            return optimizer.order(lat, lon, candidates, returnToStart);
        }

        List<Place> best = List.of(candidates.get(0), candidates.get(1));
        double bestMinutes = Double.MAX_VALUE;
        for (int i = 0; i < candidates.size() - 1; i++) {
            for (int j = i + 1; j < candidates.size(); j++) {
                List<Place> ordered = optimizer.order(lat, lon, List.of(candidates.get(i), candidates.get(j)),
                        returnToStart);
                double minutes = estimateRouteMinutes(lat, lon, ordered, returnToStart);
                if (minutes < bestMinutes) {
                    bestMinutes = minutes;
                    best = ordered;
                }
            }
        }
        return best;
    }

    /**
     * Собирает доменную прогулку: реальный пеший маршрут по улицам, переходы между
     * точками, время пребывания, кольцо с возвратом к старту и суммарные показатели
     */
    private Walk build(double lat, double lon, List<Place> route, boolean returnToStart) {
        // Имя города нужно, чтобы исключить его из сопоставления статей Wikipedia
        // (иначе POI ложно матчится с городскими статьями по слову-названию города).
        // findNearest — чистое чтение; город уже импортирован при наборе пула
        String cityName = cityService.findNearest(lat, lon).map(City::getName).orElse(null);

        // Обогащаем выбранные точки фото/описанием параллельно (внешний API, результат кэшируется)
        Map<Long, PlaceMedia> mediaById = route.parallelStream()
                .collect(Collectors.toConcurrentMap(Place::getId, p -> enricher.enrich(p, cityName)));

        List<GeoPoint> waypoints = routeWaypoints(lat, lon, route, returnToStart);
        GeoRoute geoRoute = geoapifyClient.walkingRoute(waypoints)
                .orElseGet(() -> estimatedRoute(waypoints));

        List<WalkPoint> points = new ArrayList<>(route.size());
        long dwellMinutes = 0;

        double prevLat = lat;
        double prevLon = lon;
        for (int i = 0; i < route.size(); i++) {
            Place p = route.get(i);
            RouteLeg leg = legOrEstimate(geoRoute, i, prevLat, prevLon, p.getLat(), p.getLon());
            long legMeters = leg.distanceMeters();
            double legMinutes = leg.timeSeconds() / 60.0;
            int dwell = estimator.dwellMinutes(p.getCategory());
            PlaceMedia media = mediaById.getOrDefault(p.getId(), PlaceMedia.EMPTY);

            points.add(new WalkPoint(p, i + 1, legMeters, legMinutes, dwell, media));
            dwellMinutes += dwell;

            prevLat = p.getLat();
            prevLon = p.getLon();
        }

        Long cityId = route.stream().map(Place::getCityId).filter(Objects::nonNull).findFirst().orElse(null);
        long roundedWalk = Math.round(geoRoute.timeSeconds() / 60.0);
        return new Walk(
                new GeoPoint(lat, lon),
                cityId,
                returnToStart,
                points,
                geoRoute,
                geoRoute.distanceMeters(),
                roundedWalk,
                dwellMinutes,
                roundedWalk + dwellMinutes);
    }

    /**
     * Оценка длительности маршрута (мин) для заданного порядка точек — с возвратом
     * к старту, если кольцо. Используется при подборе количества точек
     */
    private double estimateRouteMinutes(double lat, double lon, List<Place> ordered, boolean returnToStart) {
        double minutes = 0;
        double prevLat = lat;
        double prevLon = lon;
        for (Place p : ordered) {
            long legMeters = estimator.legMeters(
                    GeoDistance.haversineMeters(prevLat, prevLon, p.getLat(), p.getLon()));
            minutes += estimator.walkMinutes(legMeters) + estimator.dwellMinutes(p.getCategory());
            prevLat = p.getLat();
            prevLon = p.getLon();
        }
        if (returnToStart) {
            long returnMeters = estimator.legMeters(GeoDistance.haversineMeters(prevLat, prevLon, lat, lon));
            minutes += estimator.walkMinutes(returnMeters);
        }
        return minutes;
    }

    /**
     * Радиус сбора кандидатов из бюджета времени: чем длиннее прогулка, тем шире
     * ищем. Ограничен снизу и сверху, чтобы пул был и непустым, и не разъезжался
     */
    private int searchRadius(int durationMinutes) {
        return Math.max(800, Math.min(3000, durationMinutes * 20));
    }

    private static List<GeoPoint> routeWaypoints(double lat, double lon, List<Place> route, boolean returnToStart) {
        List<GeoPoint> waypoints = new ArrayList<>(route.size() + 2);
        GeoPoint start = new GeoPoint(lat, lon);
        waypoints.add(start);
        for (Place p : route) {
            waypoints.add(new GeoPoint(p.getLat(), p.getLon()));
        }
        if (returnToStart) {
            waypoints.add(start);
        }
        return waypoints;
    }

    private GeoRoute estimatedRoute(List<GeoPoint> waypoints) {
        List<RouteLeg> legs = new ArrayList<>(Math.max(0, waypoints.size() - 1));
        List<List<List<Double>>> lines = new ArrayList<>(Math.max(0, waypoints.size() - 1));
        long totalMeters = 0;
        double totalSeconds = 0;

        for (int i = 0; i < waypoints.size() - 1; i++) {
            GeoPoint from = waypoints.get(i);
            GeoPoint to = waypoints.get(i + 1);
            long legMeters = estimator.legMeters(
                    GeoDistance.haversineMeters(from.lat(), from.lon(), to.lat(), to.lon()));
            long legSeconds = Math.round(estimator.walkMinutes(legMeters) * 60);
            legs.add(new RouteLeg(legMeters, legSeconds, List.of()));
            lines.add(List.of(
                    List.of(from.lon(), from.lat()),
                    List.of(to.lon(), to.lat())));
            totalMeters += legMeters;
            totalSeconds += legSeconds;
        }

        return new GeoRoute(
                new RouteGeometry("MultiLineString", lines),
                totalMeters,
                Math.round(totalSeconds),
                legs,
                true);
    }

    private RouteLeg legOrEstimate(GeoRoute route, int legIndex,
                                   double fromLat, double fromLon, double toLat, double toLon) {
        if (legIndex < route.legs().size()) {
            return route.legs().get(legIndex);
        }
        long legMeters = estimator.legMeters(GeoDistance.haversineMeters(fromLat, fromLon, toLat, toLon));
        long legSeconds = Math.round(estimator.walkMinutes(legMeters) * 60);
        return new RouteLeg(legMeters, legSeconds, List.of());
    }
}
