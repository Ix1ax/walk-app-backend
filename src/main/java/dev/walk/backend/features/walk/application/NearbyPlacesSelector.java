package dev.walk.backend.features.walk.application;

import dev.walk.backend.features.geo.domain.GeoDistance;
import dev.walk.backend.features.place.domain.Place;
import dev.walk.backend.features.place.domain.PlaceCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Ilya Samsonov
 * Отбор кандидатов для прогулки из пула мест рядом. Возвращает приоритетный
 * список: места разных категорий чередуются (round-robin), чтобы первые точки
 * были разнообразными по типу. Порядок намеренно **рандомизирован** — внутри
 * категории места берутся взвешенно-случайно (ближние вероятнее, но не всегда),
 * и сам порядок категорий случаен. Поэтому повторная генерация из той же точки
 * даёт другой маршрут (точки могут повторяться, набор и последовательность —
 * обычно нет). Близость остаётся в приоритете, так что прогулка не разъезжается
 */
@Component
public class NearbyPlacesSelector {

    /**
     * Сглаживание в весе близости (м): без него место у самой точки старта
     * получало бы непропорционально большой вес. Чем больше — тем «ровнее» рандом
     */
    private static final double DISTANCE_SMOOTHING_METERS = 100.0;

    /**
     * Раскладывает пул в приоритет включения со случайностью: категории чередуются
     * в случайном порядке, внутри категории — взвешенно-случайно с уклоном к близким
     */
    public List<Place> rank(double lat, double lon, List<Place> pool) {
        // Группируем по категории
        Map<PlaceCategory, List<Place>> byCategory = new LinkedHashMap<>();
        for (Place p : pool) {
            byCategory.computeIfAbsent(p.getCategory(), c -> new ArrayList<>()).add(p);
        }

        // Внутри каждой категории — взвешенно-случайный порядок (ближе = вероятнее)
        List<Deque<Place>> queues = new ArrayList<>();
        for (List<Place> group : byCategory.values()) {
            queues.add(new ArrayDeque<>(weightedShuffle(lat, lon, group)));
        }
        // Случайный порядок категорий — чтобы вперёд выходили разные типы мест
        Collections.shuffle(queues);

        // Round-robin: по одному месту из каждой категории за круг, пока есть что брать
        List<Place> ranked = new ArrayList<>(pool.size());
        boolean took = true;
        while (took) {
            took = false;
            for (Deque<Place> q : queues) {
                Place p = q.poll();
                if (p != null) {
                    ranked.add(p);
                    took = true;
                }
            }
        }
        return ranked;
    }

    /**
     * Взвешенно-случайная перестановка мест группы: вес тем больше, чем ближе место
     * к старту. Метод Эфраимидиса–Спиракиса (key = u^(1/w)) — корректная выборка без
     * повторов по весам. Близкие места обычно идут раньше, но порядок каждый раз иной
     */
    private static List<Place> weightedShuffle(double lat, double lon, List<Place> group) {
        record Keyed(Place place, double key) {
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        List<Keyed> keyed = new ArrayList<>(group.size());
        for (Place p : group) {
            double dist = GeoDistance.haversineMeters(lat, lon, p.getLat(), p.getLon());
            // Обратный квадрат расстояния — сильный уклон к близости: дальние точки
            // изредка попадают (нужно разнообразие), но прогулка не разъезжается
            double base = dist + DISTANCE_SMOOTHING_METERS;
            double weight = 1.0 / (base * base);
            double u = rnd.nextDouble();
            double key = Math.pow(u, 1.0 / weight); // больший вес → key ближе к 1 → раньше
            keyed.add(new Keyed(p, key));
        }
        keyed.sort(Comparator.comparingDouble(Keyed::key).reversed());
        return keyed.stream().map(Keyed::place).toList();
    }
}
