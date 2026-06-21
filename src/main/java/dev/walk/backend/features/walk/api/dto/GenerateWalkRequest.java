package dev.walk.backend.features.walk.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * @author Ilya Samsonov
 * Запрос на генерацию прогулки: точка старта и желаемая длительность в минутах.
 * Под бюджет длительности подбирается 4–6 интересных точек разных категорий.
 * {@code returnToStart} — вернуться ли к старту (замкнутое кольцо) или идти
 * дальше до последней точки (открытый путь); по умолчанию кольцо
 */
public record GenerateWalkRequest(
        @DecimalMin("-90") @DecimalMax("90") double lat,
        @DecimalMin("-180") @DecimalMax("180") double lon,
        @Min(15) @Max(240) Integer durationMinutes,
        Boolean returnToStart
) {
    /**
     * Длительность по умолчанию, если клиент её не задал
     */
    private static final int DEFAULT_DURATION_MINUTES = 60;

    public int durationOrDefault() {
        return durationMinutes != null ? durationMinutes : DEFAULT_DURATION_MINUTES;
    }

    public boolean returnToStartOrDefault() {
        return returnToStart == null || returnToStart;
    }
}
