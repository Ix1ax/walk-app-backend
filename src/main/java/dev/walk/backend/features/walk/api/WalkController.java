package dev.walk.backend.features.walk.api;

import dev.walk.backend.features.walk.api.dto.GenerateWalkRequest;
import dev.walk.backend.features.walk.api.dto.WalkResponse;
import dev.walk.backend.features.walk.application.WalkGenerator;
import dev.walk.backend.features.walk.domain.Walk;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Ilya Samsonov
 */
@RestController
@RequestMapping("/api/v1/walks")
@RequiredArgsConstructor
public class WalkController {

    private final WalkGenerator generator;

    /**
     * Генерирует превью пешей прогулки от точки старта под желаемую длительность.
     * Ничего не сохраняет — сохранение появится на этапе 5
     */
    @PostMapping("/generate")
    public WalkResponse generate(@Valid @RequestBody GenerateWalkRequest request) {
        Walk walk = generator.generate(
                request.lat(), request.lon(), request.durationOrDefault(), request.returnToStartOrDefault());
        return WalkResponse.from(walk);
    }
}
