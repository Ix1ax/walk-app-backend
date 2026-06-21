package dev.walk.backend.features.geo.domain;

import java.util.List;

/**
 * Walking route leg between two consecutive waypoints.
 */
public record RouteLeg(
        long distanceMeters,
        long timeSeconds,
        List<RouteStep> steps
) {
}
