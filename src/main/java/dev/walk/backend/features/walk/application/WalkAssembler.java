package dev.walk.backend.features.walk.application;

import dev.walk.backend.features.city.application.CityService;
import dev.walk.backend.features.city.domain.City;
import dev.walk.backend.features.geo.GeoapifyClient;
import dev.walk.backend.features.geo.domain.GeoDistance;
import dev.walk.backend.features.geo.domain.GeoPoint;
import dev.walk.backend.features.geo.domain.GeoRoute;
import dev.walk.backend.features.geo.domain.RouteGeometry;
import dev.walk.backend.features.geo.domain.RouteLeg;
import dev.walk.backend.features.place.application.PlaceEnricher;
import dev.walk.backend.features.place.domain.Place;
import dev.walk.backend.features.place.domain.PlaceMedia;
import dev.walk.backend.features.walk.domain.Walk;
import dev.walk.backend.features.walk.domain.WalkPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author Ilya Samsonov
 * Собирает доменную прогулку {@link Walk} из старта и УЖЕ упорядоченного списка
 * мест: строит реальный пеший маршрут по улицам (Geoapify, с фолбэком на прямые
 * линии), считает переходы/время пребывания, обогащает точки фото+описанием и
 * сводит показатели. Единая сборка для генерации, замены точки и перестроения
 * маршрута — упорядочивание точек делают вызывающие (генератор/оптимизатор)
 */
@Service
@RequiredArgsConstructor
public class WalkAssembler {

    private final WalkTimeEstimator estimator;
    private final PlaceEnricher enricher;
    private final CityService cityService;
    private final GeoapifyClient geoapifyClient;

    /**
     * Собирает прогулку от точки старта через переданные места в заданном порядке.
     * {@code returnToStart} — замкнуть ли кольцо возвратом к старту
     */
    public Walk assemble(double lat, double lon, List<Place> route, boolean returnToStart) {
        // Имя города нужно, чтобы исключить его из сопоставления статей Wikipedia
        // (иначе POI ложно матчится с городскими статьями по слову-названию города).
        // findNearest — чистое чтение; город уже импортирован при наборе пула
        String cityName = cityService.findNearest(lat, lon).map(City::getName).orElse(null);

        // Обогащаем выбранные точки фото/описанием параллельно (внешний API, результат кэшируется).
        // Одно и то же место могло попасть дважды — собираем медиа по id без конфликтов
        Map<Long, PlaceMedia> mediaById = route.parallelStream()
                .map(Place::getId)
                .distinct()
                .collect(Collectors.toConcurrentMap(
                        id -> id,
                        id -> enricher.enrich(placeById(route, id), cityName),
                        (a, b) -> a,
                        ConcurrentHashMap::new));

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

    private static Place placeById(List<Place> route, long id) {
        for (Place p : route) {
            if (p.getId() == id) {
                return p;
            }
        }
        throw new IllegalStateException("Место id=" + id + " пропало из маршрута");
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
