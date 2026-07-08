package dev.walk.backend.features.walk.api.dto;

import dev.walk.backend.features.geo.domain.GeoDistance;
import dev.walk.backend.features.geo.domain.GeoPoint;
import dev.walk.backend.features.place.domain.PlaceCategory;
import dev.walk.backend.features.walk.domain.WalkPointEntity;

/**
 * @author Ilya Samsonov
 * Точка сохранённой прогулки в ответе API. Поля места — снимок на момент сборки
 * (стабильны, даже если место позже скрыли). {@code visited} — пройдена ли точка
 */
public record SavedWalkPointResponse(
        int order,
        Long placeId,
        String name,
        PlaceCategory category,
        GeoPoint center,
        long distanceFromStartMeters,
        long legFromPrevMeters,
        long legFromPrevMinutes,
        int dwellMinutes,
        String imageUrl,
        String description,
        String infoUrl,
        boolean visited
) {
    public static SavedWalkPointResponse from(WalkPointEntity p, double startLat, double startLon) {
        long fromStart = Math.round(GeoDistance.haversineMeters(startLat, startLon, p.getLat(), p.getLon()));
        return new SavedWalkPointResponse(
                p.getSeq(),
                p.getPlaceId(),
                p.getName(),
                p.getCategory(),
                new GeoPoint(p.getLat(), p.getLon()),
                fromStart,
                p.getLegFromPrevMeters(),
                Math.round(p.getLegFromPrevSeconds() / 60.0),
                p.getDwellMinutes(),
                p.getImageUrl(),
                p.getDescription(),
                p.getInfoUrl(),
                p.isVisited());
    }
}
