package dev.walk.backend.features.geo.domain;

import java.util.List;

/**
 * Calculated walking route with drawable GeoJSON geometry and leg metrics.
 *
 * @param estimated true when the route is an internal straight-line fallback
 */
public record GeoRoute(
        RouteGeometry geometry,
        long distanceMeters,
        long timeSeconds,
        List<RouteLeg> legs,
        boolean estimated
) {
}
