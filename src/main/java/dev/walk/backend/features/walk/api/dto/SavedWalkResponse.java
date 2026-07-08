package dev.walk.backend.features.walk.api.dto;

import dev.walk.backend.features.geo.domain.GeoPoint;
import dev.walk.backend.features.geo.domain.GeoRoute;
import dev.walk.backend.features.walk.domain.WalkEntity;

import java.time.Instant;
import java.util.List;

/**
 * @author Ilya Samsonov
 * Сохранённая прогулка в ответе API: метрики, статус/прогресс, геометрия маршрута
 * для карты и точки в порядке обхода. Если {@code returnToStart} — маршрут
 * кольцевой ({@code start} и старт, и финиш)
 */
public record SavedWalkResponse(
        Long id,
        String status,
        GeoPoint start,
        Long cityId,
        boolean returnToStart,
        int durationMinutes,
        int pointsCount,
        int visitedCount,
        long totalDistanceMeters,
        long walkMinutes,
        long dwellMinutes,
        long totalMinutes,
        RouteResponse route,
        List<SavedWalkPointResponse> points,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * @param walk  сущность с уже подгруженными точками
     * @param route разобранная геометрия маршрута (может быть null, если не сохранилась)
     */
    public static SavedWalkResponse from(WalkEntity walk, GeoRoute route) {
        double startLat = walk.getStartLat();
        double startLon = walk.getStartLon();
        List<SavedWalkPointResponse> points = walk.getPoints().stream()
                .map(p -> SavedWalkPointResponse.from(p, startLat, startLon))
                .toList();
        return new SavedWalkResponse(
                walk.getId(),
                walk.getStatus().name(),
                new GeoPoint(startLat, startLon),
                walk.getCityId(),
                walk.isReturnToStart(),
                walk.getDurationMinutes(),
                points.size(),
                walk.visitedCount(),
                walk.getTotalDistanceMeters(),
                walk.getWalkMinutes(),
                walk.getDwellMinutes(),
                walk.getTotalMinutes(),
                route != null ? RouteResponse.from(route) : null,
                points,
                walk.getCreatedAt(),
                walk.getUpdatedAt());
    }
}
