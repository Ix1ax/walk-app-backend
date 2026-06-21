package dev.walk.backend.features.walk.api.dto;

import dev.walk.backend.features.geo.domain.RouteStep;

/**
 * Пешеходная инструкция внутри leg маршрута.
 */
public record RouteStepResponse(
        long distanceMeters,
        long durationMinutes,
        String instruction
) {
    public static RouteStepResponse from(RouteStep step) {
        return new RouteStepResponse(
                step.distanceMeters(),
                Math.round(step.timeSeconds() / 60.0),
                step.instruction());
    }
}
