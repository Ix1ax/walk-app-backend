package dev.walk.backend.features.city.api.dto;

import dev.walk.backend.features.city.domain.City;
import dev.walk.backend.features.geo.domain.GeoPoint;

/**
 * @author Ilya Samsonov
 * Город в ответе API
 */
public record CityResponse(
        Long id,
        String name,
        String slug,
        String countryCode,
        GeoPoint center
) {
    public static CityResponse from(City city) {
        return new CityResponse(
                city.getId(),
                city.getName(),
                city.getSlug(),
                city.getCountryCode(),
                new GeoPoint(city.getCenterLat(), city.getCenterLon())
        );
    }
}
