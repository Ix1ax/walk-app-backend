package dev.walk.backend.features.geo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Ilya Samsonov
 * Настройки Overpass API (OpenStreetMap) из application.yml (префикс walk.overpass).
 * {@code url} — эндпоинт интерпретатора, {@code timeoutSeconds} — таймаут запроса
 */
@ConfigurationProperties(prefix = "walk.overpass")
public record OverpassProperties(String url, Integer timeoutSeconds) {

    public int timeoutSecondsOrDefault() {
        return timeoutSeconds != null ? timeoutSeconds : 60;
    }
}
