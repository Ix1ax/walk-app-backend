package dev.walk.backend.features.geo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Ilya Samsonov
 * Настройки Geoapify (ключ и базовый URL) из application.yml (префикс walk.geoapify)
 */
@ConfigurationProperties(prefix = "walk.geoapify")
public record GeoapifyProperties(String apiKey, String baseUrl) {
}
