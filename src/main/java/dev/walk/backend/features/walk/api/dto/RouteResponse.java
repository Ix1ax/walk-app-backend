package dev.walk.backend.features.walk.api.dto;

import dev.walk.backend.features.geo.domain.GeoRoute;
import dev.walk.backend.features.geo.domain.RouteGeometry;

import java.util.List;

/**
 * Геометрия и метрики маршрута, готовые для отрисовки на карте как GeoJSON.
 */
public record RouteResponse(
        RouteGeometry geometry,
        long distanceMeters,
        long durationMinutes,
        boolean estimated,
        List<RouteLegResponse> legs
) {
    public static RouteResponse from(GeoRoute route) {
        List<RouteLegResponse> legs = route.legs().stream()
                .map(RouteLegResponse::from)
                .toList();
        return new RouteResponse(
                route.geometry(),
                route.distanceMeters(),
                Math.round(route.timeSeconds() / 60.0),
                route.estimated(),
                legs);
    }
}
