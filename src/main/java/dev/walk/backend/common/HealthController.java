package dev.walk.backend.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * @author Ilya Samsonov
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Value("${spring.application.name}")
    private String appName;


    @GetMapping
    public Map<String, String> health() {
        return Map.of(
                "name", appName,
                "status", "UP"
        );
    }
}
