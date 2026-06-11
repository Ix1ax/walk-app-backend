package dev.walk.backend.features.place.api;

import dev.walk.backend.features.place.application.PlaceService;
import dev.walk.backend.features.place.api.dto.PlaceResponse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author Ilya Samsonov
 */
@Validated
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceService service;

    /**
     * Места рядом со стартовой точкой. {@code radius} — радиус поиска в метрах,
     * {@code limit} — максимум точек в ответе
     */
    @GetMapping("/nearby")
    public List<PlaceResponse> nearby(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon,
            @RequestParam(defaultValue = "1000") @Min(100) @Max(5000) int radius,
            @RequestParam(defaultValue = "30") @Min(1) @Max(50) int limit) {
        return service.nearby(lat, lon, radius, limit);
    }
}
