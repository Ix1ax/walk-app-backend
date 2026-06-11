package dev.walk.backend.features.city.api;

import dev.walk.backend.features.city.application.CityService;
import dev.walk.backend.features.city.api.dto.CityResponse;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author Ilya Samsonov
 */
@Validated
@RestController
@RequestMapping("/api/v1/cities")
public class CityController {

    private final CityService service;

    public CityController(CityService service) {
        this.service = service;
    }

    /** Весь список поддерживаемых городов. */
    @GetMapping
    public List<CityResponse> all() {
        return service.search(null);
    }

    /** Поиск городов по части названия. {@code query} необязателен (пустой — весь список). */
    @GetMapping("/search")
    public List<CityResponse> search(@RequestParam(required = false) String query) {
        return service.search(query);
    }

    /** Текущий город по координатам пользователя. */
    @GetMapping("/current")
    public CityResponse current(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon) {
        return service.current(lat, lon);
    }

    @GetMapping("/{slug}")
    public CityResponse bySlug(@PathVariable String slug) {
        return service.getBySlug(slug);
    }
}
