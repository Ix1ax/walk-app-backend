package dev.walk.backend.features.walk.application;

import dev.walk.backend.features.geo.domain.GeoDistance;
import dev.walk.backend.features.place.domain.Place;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Ilya Samsonov
 * Оптимизация порядка точек прогулки. Два режима: кольцо (старт → точки → старт)
 * и открытый путь (старт → точки, без возврата) — разница только в учёте обратного
 * плеча в стоимости. Точек мало (4–6), поэтому для них считаем оптимум честным
 * перебором всех перестановок; на случай большего набора есть запасной «ближайший
 * сосед». Расстояния — по прямой (гаверсинус); порядок от поправки на извилистость
 * не зависит, поэтому считаем без неё
 */
@Component
public class RouteOptimizer {

    /**
     * До скольких точек включительно перебираем все перестановки (n! ≤ 5040)
     */
    private static final int EXACT_MAX_POINTS = 7;

    /**
     * Упорядочивает точки в кратчайший маршрут от стартовой точки. Если
     * {@code returnToStart} — маршрут кольцевой (учитываем возврат к старту),
     * иначе открытый путь до последней точки. Возвращает новый список в порядке
     * обхода (без точки старта)
     */
    public List<Place> order(double startLat, double startLon, List<Place> places, boolean returnToStart) {
        if (places.size() <= 1) {
            return new ArrayList<>(places);
        }
        return places.size() <= EXACT_MAX_POINTS
                ? exact(startLat, startLon, places, returnToStart)
                : nearestNeighbor(startLat, startLon, places);
    }

    /**
     * Точный оптимум перебором всех перестановок порядка обхода
     */
    private List<Place> exact(double startLat, double startLon, List<Place> places, boolean returnToStart) {
        int[] order = new int[places.size()];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        int[] best = order.clone();
        double[] bestCost = {routeCost(startLat, startLon, places, order, returnToStart)};
        permute(order, 0, places, startLat, startLon, returnToStart, best, bestCost);

        List<Place> result = new ArrayList<>(places.size());
        for (int idx : best) {
            result.add(places.get(idx));
        }
        return result;
    }

    private void permute(int[] order, int k, List<Place> places, double startLat, double startLon,
                         boolean returnToStart, int[] best, double[] bestCost) {
        if (k == order.length - 1) {
            double cost = routeCost(startLat, startLon, places, order, returnToStart);
            if (cost < bestCost[0]) {
                bestCost[0] = cost;
                System.arraycopy(order, 0, best, 0, order.length);
            }
            return;
        }
        for (int i = k; i < order.length; i++) {
            swap(order, k, i);
            permute(order, k + 1, places, startLat, startLon, returnToStart, best, bestCost);
            swap(order, k, i);
        }
    }

    /**
     * Жадный «ближайший сосед» от старта — запасной вариант для большого набора
     */
    private List<Place> nearestNeighbor(double startLat, double startLon, List<Place> places) {
        List<Place> remaining = new ArrayList<>(places);
        List<Place> ordered = new ArrayList<>(places.size());
        double curLat = startLat;
        double curLon = startLon;
        while (!remaining.isEmpty()) {
            Place nearest = null;
            double bestDist = Double.MAX_VALUE;
            for (Place p : remaining) {
                double d = GeoDistance.haversineMeters(curLat, curLon, p.getLat(), p.getLon());
                if (d < bestDist) {
                    bestDist = d;
                    nearest = p;
                }
            }
            ordered.add(nearest);
            remaining.remove(nearest);
            curLat = nearest.getLat();
            curLon = nearest.getLon();
        }
        return ordered;
    }

    /**
     * Длина маршрута: старт → точки в заданном порядке (+ возврат к старту, если
     * {@code returnToStart})
     */
    private double routeCost(double startLat, double startLon, List<Place> places,
                             int[] order, boolean returnToStart) {
        double total = 0;
        double prevLat = startLat;
        double prevLon = startLon;
        for (int idx : order) {
            Place p = places.get(idx);
            total += GeoDistance.haversineMeters(prevLat, prevLon, p.getLat(), p.getLon());
            prevLat = p.getLat();
            prevLon = p.getLon();
        }
        if (returnToStart) {
            total += GeoDistance.haversineMeters(prevLat, prevLon, startLat, startLon);
        }
        return total;
    }

    private static void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }
}
