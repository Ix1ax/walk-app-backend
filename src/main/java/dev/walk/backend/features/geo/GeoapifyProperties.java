package dev.walk.backend.features.geo;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Ilya Samsonov
 * Настройки Geoapify из application.yml (префикс walk.geoapify). {@code baseUrl} может
 * указывать на Cloudflare Worker-прокси (чтобы запросы шли из РФ без VPN); тогда
 * {@code proxyToken} — секрет доступа к воркеру (он сам подставляет ключ Geoapify)
 */
@ConfigurationProperties(prefix = "walk.geoapify")
public record GeoapifyProperties(String apiKey, String baseUrl, String proxyToken) {
}
