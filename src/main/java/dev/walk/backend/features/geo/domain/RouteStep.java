package dev.walk.backend.features.geo.domain;

/**
 * Step inside a walking route leg. Instruction can be null for estimated routes.
 */
public record RouteStep(
        long distanceMeters,
        long timeSeconds,
        String instruction
) {
}
