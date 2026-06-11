package dev.walk.backend.features.place.api.dto;

import dev.walk.backend.features.geo.domain.GeoDistance;
import dev.walk.backend.features.geo.domain.GeoPoint;
import dev.walk.backend.features.place.domain.Place;
import dev.walk.backend.features.place.domain.PlaceCategory;

/**
 * @author Ilya Samsonov
 * Место в ответе API. {@code distanceMeters} — расстояние по прямой от точки запроса
 */
public record PlaceResponse(
        Long id,
        String name,
        PlaceCategory category,
        GeoPoint center,
        Long cityId,
        long distanceMeters
) {
    public static PlaceResponse from(Place place, double fromLat, double fromLon) {
        long distance = Math.round(GeoDistance.haversineMeters(fromLat, fromLon, place.getLat(), place.getLon()));
        return new PlaceResponse(
                place.getId(),
                place.getName(),
                place.getCategory(),
                new GeoPoint(place.getLat(), place.getLon()),
                place.getCityId(),
                distance
        );
    }
}
