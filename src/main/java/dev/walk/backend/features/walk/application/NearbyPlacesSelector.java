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
 * обычно нет). Близость остаётся в приоритете, так что прогулка не разъезжается.
 * Качество: значимым местам (notable) даётся приоритет, а точки ближе
 * {@link #MIN_SPACING_METERS} друг к другу схлопываются — чтобы в маршрут не лезли
 * дубли (node+way одного объекта) и стопки мемориальных табличек
 */
@Component
public class NearbyPlacesSelector {

    /**
     * Сглаживание в весе близости (м): без него место у самой точки старта
     * получало бы непропорционально большой вес. Чем больше — тем «ровнее» рандом
     */
    private static final double DISTANCE_SMOOTHING_METERS = 100.0;
    /**
     * Множитель веса для значимых мест — приоритет точкам притяжения над случайными
     * табличками/кафе (мягкий: не исключает остальных, лишь повышает их шанс)
     */
    private static final double NOTABLE_WEIGHT_BOOST = 6.0;
    /**
     * Минимальное расстояние между отобранными точками (м): ближе — считаем дублем/
     * стопкой и не берём вторую. Снимает «три памятника в 5 метрах»
     */
    private static final double MIN_SPACING_METERS = 50.0;

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

        // Round-robin: по одному месту из каждой категории за круг, пока есть что брать.
        // Точки ближе MIN_SPACING к уже выбранным отбрасываем (дубли/стопки)
        List<Place> ranked = new ArrayList<>(pool.size());
        boolean took = true;
        while (took) {
            took = false;
            for (Deque<Place> q : queues) {
                Place p = q.poll();
                if (p == null) {
                    continue;
                }
                took = true;
                if (!tooClose(p, ranked)) {
                    ranked.add(p);
                }
            }
        }
        return ranked;
    }

    /**
     * Есть ли среди уже выбранных точка ближе {@link #MIN_SPACING_METERS} к {@code p}
     */
    private static boolean tooClose(Place p, List<Place> chosen) {
        for (Place c : chosen) {
            if (GeoDistance.haversineMeters(p.getLat(), p.getLon(), c.getLat(), c.getLon()) < MIN_SPACING_METERS) {
                return true;
            }
        }
        return false;
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
            if (p.isNotable()) {
                weight *= NOTABLE_WEIGHT_BOOST; // приоритет точкам притяжения
            }
            double u = rnd.nextDouble();
            double key = Math.pow(u, 1.0 / weight); // больший вес → key ближе к 1 → раньше
            keyed.add(new Keyed(p, key));
        }
        keyed.sort(Comparator.comparingDouble(Keyed::key).reversed());
        return keyed.stream().map(Keyed::place).toList();
    }
}
