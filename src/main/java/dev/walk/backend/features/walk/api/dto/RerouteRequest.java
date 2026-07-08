package dev.walk.backend.features.walk.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

/**
 * @author Ilya Samsonov
 * Запрос на перестроение маршрута сохранённой прогулки от нового местоположения:
 * пользователь свернул с маршрута — строим эффективный путь от текущей точки через
 * ещё не посещённые места (набор мест сохраняется, меняется порядок и старт)
 */
public record RerouteRequest(
        @DecimalMin("-90") @DecimalMax("90") double lat,
        @DecimalMin("-180") @DecimalMax("180") double lon
) {
}
