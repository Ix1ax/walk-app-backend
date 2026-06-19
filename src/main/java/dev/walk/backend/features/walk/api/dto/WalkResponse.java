package dev.walk.backend.features.walk.api.dto;

import dev.walk.backend.features.geo.domain.GeoPoint;
import dev.walk.backend.features.walk.domain.Walk;

import java.util.List;

/**
 * @author Ilya Samsonov
 * Сгенерированная прогулка в ответе API. Если {@code returnToStart} — маршрут
 * кольцевой ({@code start} это и старт, и финиш, {@code totalDistanceMeters}
 * включает возврат), иначе открытый путь до последней точки
 */
public record WalkResponse(
        GeoPoint start,
        Long cityId,
        boolean returnToStart,
        int pointsCount,
        long totalDistanceMeters,
        long walkMinutes,
        long dwellMinutes,
        long totalMinutes,
        List<WalkPointResponse> points
) {
    public static WalkResponse from(Walk walk) {
        double startLat = walk.start().lat();
        double startLon = walk.start().lon();
        List<WalkPointResponse> points = walk.points().stream()
                .map(p -> WalkPointResponse.from(p, startLat, startLon))
                .toList();
        return new WalkResponse(
                walk.start(),
                walk.cityId(),
                walk.returnToStart(),
                points.size(),
                walk.totalDistanceMeters(),
                walk.walkMinutes(),
                walk.dwellMinutes(),
                walk.totalMinutes(),
                points);
    }
}
