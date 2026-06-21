package dev.walk.backend.features.walk.api.dto;

import dev.walk.backend.features.geo.domain.GeoDistance;
import dev.walk.backend.features.geo.domain.GeoPoint;
import dev.walk.backend.features.place.domain.Place;
import dev.walk.backend.features.place.domain.PlaceCategory;
import dev.walk.backend.features.place.domain.PlaceMedia;
import dev.walk.backend.features.walk.domain.WalkPoint;

/**
 * @author Ilya Samsonov
 * Точка прогулки в ответе API. {@code imageUrl}/{@code description}/{@code infoUrl}
 * — фото и краткое описание места (из Wikipedia), могут быть null, если данных нет
 */
public record WalkPointResponse(
        int order,
        Long id,
        String name,
        PlaceCategory category,
        GeoPoint center,
        long distanceFromStartMeters,
        long legFromPrevMeters,
        long legFromPrevMinutes,
        int dwellMinutes,
        String imageUrl,
        String description,
        String infoUrl
) {
    public static WalkPointResponse from(WalkPoint point, double startLat, double startLon) {
        Place p = point.place();
        long fromStart = Math.round(GeoDistance.haversineMeters(startLat, startLon, p.getLat(), p.getLon()));
        PlaceMedia media = point.media() != null ? point.media() : PlaceMedia.EMPTY;
        return new WalkPointResponse(
                point.order(),
                p.getId(),
                p.getName(),
                p.getCategory(),
                new GeoPoint(p.getLat(), p.getLon()),
                fromStart,
                point.legFromPrevMeters(),
                Math.round(point.legFromPrevMinutes()),
                point.dwellMinutes(),
                media.imageUrl(),
                media.description(),
                media.sourceUrl());
    }
}
