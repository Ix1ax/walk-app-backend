package dev.walk.backend.features.walk.api.dto;

import dev.walk.backend.features.geo.domain.GeoPoint;
import dev.walk.backend.features.walk.domain.WalkEntity;

import java.time.Instant;

/**
 * @author Ilya Samsonov
 * Краткая карточка прогулки для списка истории (без геометрии и точек)
 */
public record WalkSummaryResponse(
        Long id,
        String status,
        GeoPoint start,
        Long cityId,
        boolean returnToStart,
        int durationMinutes,
        int pointsCount,
        int visitedCount,
        long totalDistanceMeters,
        long totalMinutes,
        Instant createdAt,
        Instant updatedAt
) {
    public static WalkSummaryResponse from(WalkEntity walk) {
        return new WalkSummaryResponse(
                walk.getId(),
                walk.getStatus().name(),
                new GeoPoint(walk.getStartLat(), walk.getStartLon()),
                walk.getCityId(),
                walk.isReturnToStart(),
                walk.getDurationMinutes(),
                walk.getPoints().size(),
                walk.visitedCount(),
                walk.getTotalDistanceMeters(),
                walk.getTotalMinutes(),
                walk.getCreatedAt(),
                walk.getUpdatedAt());
    }
}
