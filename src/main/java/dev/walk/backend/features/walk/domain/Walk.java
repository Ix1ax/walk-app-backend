package dev.walk.backend.features.walk.domain;

import dev.walk.backend.features.geo.domain.GeoPoint;

import java.util.List;

/**
 * @author Ilya Samsonov
 * Сгенерированная пешая прогулка (превью). Маршрут либо кольцевой (от старта
 * через точки и обратно к старту), либо открытый (до последней точки) — см.
 * {@code returnToStart}. Не персистится — сохранение появится на этапе 5
 *
 * @param start              точка старта (и финиша для кольца)
 * @param cityId             город прогулки (может быть null, если не определён)
 * @param returnToStart      кольцо (true) или открытый путь без возврата (false)
 * @param points             точки маршрута в порядке обхода
 * @param totalDistanceMeters полная длина маршрута (с возвратом к старту, если кольцо; с поправкой на улицы)
 * @param walkMinutes        суммарное время в движении, мин
 * @param dwellMinutes       суммарное время пребывания в точках, мин
 * @param totalMinutes       общая длительность прогулки, мин (движение + пребывание)
 */
public record Walk(
        GeoPoint start,
        Long cityId,
        boolean returnToStart,
        List<WalkPoint> points,
        long totalDistanceMeters,
        long walkMinutes,
        long dwellMinutes,
        long totalMinutes
) {
}
