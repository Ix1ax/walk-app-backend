package dev.walk.backend.features.walk.api.dto;

import dev.walk.backend.features.geo.domain.RouteLeg;

import java.util.List;

/**
 * Пеший отрезок маршрута между соседними waypoint'ами.
 */
public record RouteLegResponse(
        long distanceMeters,
        long durationMinutes,
        List<RouteStepResponse> steps
) {
    public static RouteLegResponse from(RouteLeg leg) {
        List<RouteStepResponse> steps = leg.steps().stream()
                .map(RouteStepResponse::from)
                .toList();
        return new RouteLegResponse(
                leg.distanceMeters(),
                Math.round(leg.timeSeconds() / 60.0),
                steps);
    }
}
