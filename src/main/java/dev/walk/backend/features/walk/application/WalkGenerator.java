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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * @author Ilya Samsonov
 * Генерация пешей прогулки от стартовой точки. Берёт пул мест рядом, набирает
 * точки с разбросом по кольцам расстояния (число и радиус растут с бюджетом, чтобы
 * реальная длина прогулки соответствовала запросу), оптимизирует порядок обхода
 * (кольцо/открытый путь), строит реальный пеший маршрут и оценивает время. Ничего
 * не сохраняет — это превью
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalkGenerator {

    /**
     * Потолок числа точек (длинные прогулки набирают больше остановок)
     */
    private static final int MAX_POINTS = 10;
    /**
     * Минимум, ниже которого прогулку не строим (нечего показывать)
     */
    private static final int MIN_POINTS = 2;
    /**
     * Сколько минут бюджета приходится на одну точку — из этого считаем, сколько
     * остановок набрать (длиннее прогулка → больше точек)
     */
    private static final double MINUTES_PER_POINT = 16.0;
    /**
     * Множитель веса notable-мест при случайном выборе внутри кольца расстояния
     */
    private static final double NOTABLE_PICK_BOOST = 3.0;
    /**
     * Радиус сбора пула (макс. покрытие зоны). Радиус разброса точек под конкретный
     * бюджет калибруется внутри этого предела
     */
    private static final int POOL_RADIUS_METERS = 3000;
    /**
     * Доля бюджета, в которую целимся оценкой. Реальный пеший маршрут по улицам
     * длиннее нашей оценки (прямая×1.3), причём для длинных многоэтапных маршрутов
     * сильнее — поэтому фактор УБЫВАЕТ с числом точек: {@code BASE - SLOPE*(points-2)}
     */
    private static final double TARGET_FACTOR_BASE = 0.95;
    private static final double TARGET_FACTOR_SLOPE = 0.035;
    /**
     * Сколько случайных финальных раскладок пробуем, выбирая лучшую по бюджету —
     * режет «разъехавшиеся» маршруты, сохраняя вариативность
     */
    private static final int FINAL_TRIES = 8;

    private final PlaceService placeService;
    private final NearbyPlacesSelector selector;
    private final RouteOptimizer optimizer;
    private final WalkTimeEstimator estimator;
    private final PlaceEnricher enricher;
    private final CityService cityService;
    private final GeoapifyClient geoapifyClient;

    public Walk generate(double lat, double lon, int durationMinutes, boolean returnToStart) {
        List<Place> pool = placeService.poolNearby(lat, lon, POOL_RADIUS_METERS);
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
     * Подбирает точки так, чтобы РЕАЛЬНАЯ длина прогулки масштабировалась с бюджетом
     * (а не добивалась «стоянками»). Число точек растёт с бюджетом, а сами точки
     * разносятся по кольцам расстояния [0..radius] — поэтому длинная прогулка идёт
     * широким кольцом и физически занимает больше времени. Внутри кольца точка
     * берётся случайно (с приоритетом notable) — отсюда разные маршруты при повторе
     */
    private List<Place> chooseRoute(double lat, double lon, List<Place> ranked,
                                    int durationMinutes, boolean returnToStart) {
        int targetPoints = clamp((int) Math.round(durationMinutes / MINUTES_PER_POINT), MIN_POINTS, MAX_POINTS);
        double factor = Math.max(0.7, Math.min(0.95, TARGET_FACTOR_BASE - TARGET_FACTOR_SLOPE * (targetPoints - 2)));
        double target = durationMinutes * factor;

        // Калибруем радиус разброса так, чтобы оценочное время прогулки ≈ бюджету:
        // больше радиус → шире кольцо → длиннее путь. Бинарный поиск по детерминированному
        // разбросу (без случайности, чтобы оценка была стабильной)
        double lo = 200;
        double hi = POOL_RADIUS_METERS;
        double chosenR = lo;
        for (int it = 0; it < 8; it++) {
            double r = (lo + hi) / 2;
            List<Place> probe = optimizer.order(lat, lon,
                    pickSpread(lat, lon, ranked, targetPoints, r, false), returnToStart);
            if (estimateTotalMinutes(lat, lon, probe, returnToStart) > target) {
                hi = r;
            } else {
                lo = r;
                chosenR = r;
            }
        }

        // Финальный набор: пробуем несколько случайных раскладок на найденном радиусе
        // и берём лучшую по попаданию в бюджет (перелёт штрафуем сильнее). Так
        // сохраняется вариативность, но отсекаются «разъехавшиеся» наборы
        List<Place> best = null;
        double bestErr = Double.MAX_VALUE;
        for (int k = 0; k < FINAL_TRIES; k++) {
            List<Place> spread = pickSpread(lat, lon, ranked, targetPoints, chosenR, true);
            if (spread.size() < MIN_POINTS) {
                continue;
            }
            List<Place> ordered = optimizer.order(lat, lon, spread, returnToStart);
            double est = estimateTotalMinutes(lat, lon, ordered, returnToStart);
            // Целимся в тот же target, что и калибровка (реальный маршрут длиннее оценки);
            // перелёт штрафуем сильнее
            double err = est <= target ? (target - est) : (est - target) * 2.0;
            if (err < bestErr) {
                bestErr = err;
                best = ordered;
            }
        }
        if (best == null) {
            best = optimizer.order(lat, lon, ranked.subList(0, Math.min(MIN_POINTS, ranked.size())), returnToStart);
        }
        return best;
    }

    /**
     * Оценочная длительность маршрута (ходьба прямая×1.3 + пребывание), для калибровки
     * радиуса. Реальное время в ответе считается потом по настоящему пешему маршруту
     */
    private double estimateTotalMinutes(double lat, double lon, List<Place> ordered, boolean returnToStart) {
        double minutes = 0;
        double prevLat = lat;
        double prevLon = lon;
        for (Place p : ordered) {
            long legMeters = estimator.legMeters(GeoDistance.haversineMeters(prevLat, prevLon, p.getLat(), p.getLon()));
            minutes += estimator.walkMinutes(legMeters) + estimator.dwellMinutes(p.getCategory());
            prevLat = p.getLat();
            prevLon = p.getLon();
        }
        if (returnToStart) {
            minutes += estimator.walkMinutes(
                    estimator.legMeters(GeoDistance.haversineMeters(prevLat, prevLon, lat, lon)));
        }
        return minutes;
    }

    /**
     * Выбирает до {@code n} точек, разнесённых по кольцам расстояния от старта: делим
     * [0..radius] на n полос и из каждой берём одну точку (взвешенно-случайно, notable
     * вероятнее). Пустые полосы добиваем ближайшими свободными. Так маршрут охватывает
     * нужный радиус, остаётся разнообразным и каждый раз другим
     */
    private List<Place> pickSpread(double lat, double lon, List<Place> candidates, int n, double radius, boolean random) {
        record Cand(Place place, double dist) {
        }
        List<Cand> cs = new ArrayList<>();
        for (Place p : candidates) {
            double d = GeoDistance.haversineMeters(lat, lon, p.getLat(), p.getLon());
            if (d <= radius) {
                cs.add(new Cand(p, d));
            }
        }
        if (cs.size() <= n) {
            return cs.stream().map(Cand::place).toList();
        }

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double band = radius / (double) n;
        List<Place> chosen = new ArrayList<>(n);
        Set<Place> used = new HashSet<>();
        for (int i = 0; i < n; i++) {
            double lo = i * band;
            double hi = (i + 1) * band;
            List<Place> inBand = new ArrayList<>();
            for (Cand c : cs) {
                if (!used.contains(c.place()) && c.dist() >= lo && c.dist() < hi) {
                    inBand.add(c.place());
                }
            }
            if (inBand.isEmpty()) {
                continue;
            }
            // random — для итогового набора (вариативность); иначе детерминированно
            // (для стабильной калибровки): notable вперёд, затем ближе к центру кольца
            Place pick;
            if (random) {
                pick = weightedRandomNotable(inBand, rnd);
            } else {
                double center = lo + band / 2;
                pick = inBand.stream()
                        .min(Comparator.<Place>comparingInt(p -> p.isNotable() ? 0 : 1)
                                .thenComparingDouble(p -> Math.abs(
                                        GeoDistance.haversineMeters(lat, lon, p.getLat(), p.getLon()) - center)))
                        .orElseThrow();
            }
            chosen.add(pick);
            used.add(pick);
        }
        // Пустые полосы → набрали меньше n: добиваем ближайшими свободными
        if (chosen.size() < n) {
            cs.stream()
                    .filter(c -> !used.contains(c.place()))
                    .sorted(Comparator.comparingDouble(Cand::dist))
                    .limit((long) n - chosen.size())
                    .forEach(c -> chosen.add(c.place()));
        }
        return chosen;
    }

    /**
     * Случайный выбор места из списка с уклоном к notable ({@link #NOTABLE_PICK_BOOST})
     */
    private static Place weightedRandomNotable(List<Place> places, ThreadLocalRandom rnd) {
        double total = 0;
        for (Place p : places) {
            total += p.isNotable() ? NOTABLE_PICK_BOOST : 1.0;
        }
        double r = rnd.nextDouble(total);
        for (Place p : places) {
            r -= p.isNotable() ? NOTABLE_PICK_BOOST : 1.0;
            if (r < 0) {
                return p;
            }
        }
        return places.get(places.size() - 1);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
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
