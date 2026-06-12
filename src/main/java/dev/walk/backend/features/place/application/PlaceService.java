package dev.walk.backend.features.place.application;

import dev.walk.backend.features.geo.domain.GeoDistance;
import dev.walk.backend.features.place.api.dto.PlaceResponse;
import dev.walk.backend.features.place.domain.Place;
import dev.walk.backend.features.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Ilya Samsonov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceService {

    /**
     * Потолок пула, который вытягиваем из БД перед выборкой (зона физически ≤ ~300)
     */
    private static final int POOL_CAP = 1000;

    private final PlaceRepository repository;
    private final PlaceImporter importer;

    /**
     * Места рядом со стартом. Если зона ещё не загружена из Geoapify (или устарела по
     * TTL) — подтягиваем её один раз большим радиусом по всем категориям. {@code radius}
     * фильтрует пул, а {@code limit} точек выбираются РАВНОМЕРНО ПО РАССТОЯНИЮ —
     * чтобы в выборке были и близкие, и дальние, а не только ближайшие
     */
    public List<PlaceResponse> nearby(double lat, double lon, int radiusMeters, int limit) {
        int cellLat = (int) Math.round(lat * 100);
        int cellLon = (int) Math.round(lon * 100);

        if (!importer.isCovered(cellLat, cellLon)) {
            try {
                importer.importArea(lat, lon, cellLat, cellLon);
            } catch (DataIntegrityViolationException e) {
                // Гонка: параллельный запрос уже грузит эту зону — просто перечитаем из БД
                log.info("Зона уже загружается параллельным запросом — читаем из БД (cell={},{})", cellLat, cellLon);
            }
        }

        List<Place> pool = repository.findNearby(lat, lon, radiusMeters, POOL_CAP);
        List<Place> selected = spreadByDistance(pool, lat, lon, limit);
        return selected.stream().map(p -> PlaceResponse.from(p, lat, lon)).toList();
    }

    /**
     * Из пула (в радиусе) выбирает {@code limit} точек, равномерно распределённых по
     * расстоянию от стартовой точки: ближайшая и дальняя гарантированно попадают,
     * остальные — на равных шагах по дистанции между ними. Возвращает по возрастанию
     * расстояния. Если в пуле точек не больше {@code limit} — отдаёт все
     */
    private static List<Place> spreadByDistance(List<Place> pool, double lat, double lon, int limit) {
        int n = pool.size();
        if (n <= limit) {
            return pool;
        }

        record Item(Place place, double dist) {
        }
        List<Item> items = new ArrayList<>(n);
        for (Place p : pool) {
            items.add(new Item(p, GeoDistance.haversineMeters(lat, lon, p.getLat(), p.getLon())));
        }
        items.sort(Comparator.comparingDouble(Item::dist));

        double dMin = items.get(0).dist();
        double dMax = items.get(n - 1).dist();
        double denom = Math.max(1, limit - 1);

        boolean[] used = new boolean[n];
        List<Item> picked = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            double target = dMin + (dMax - dMin) * i / denom;
            int best = -1;
            double bestDiff = Double.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                if (used[j]) {
                    continue;
                }
                double diff = Math.abs(items.get(j).dist() - target);
                if (diff < bestDiff) {
                    bestDiff = diff;
                    best = j;
                }
            }
            used[best] = true;
            picked.add(items.get(best));
        }

        picked.sort(Comparator.comparingDouble(Item::dist));
        return picked.stream().map(Item::place).toList();
    }
}
