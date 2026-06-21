package dev.walk.backend.features.geo.domain;

import java.util.List;

/**
 * GeoJSON geometry for the calculated route. Coordinates are in GeoJSON order:
 * longitude, latitude.
 */
public record RouteGeometry(
        String type,
        List<List<List<Double>>> coordinates
) {
}
