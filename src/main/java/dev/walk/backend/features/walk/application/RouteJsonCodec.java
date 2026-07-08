package dev.walk.backend.features.walk.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.walk.backend.features.geo.domain.GeoRoute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author Ilya Samsonov
 * Сериализация геометрии маршрута ({@link GeoRoute}) в JSON и обратно для хранения
 * в колонке {@code walks.route_json}. Так сохранённая прогулка восстанавливает
 * карту один-в-один, не пересчитывая маршрут
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouteJsonCodec {

    private final ObjectMapper mapper;

    public String encode(GeoRoute route) {
        if (route == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(route);
        } catch (JsonProcessingException e) {
            // Не должно случаться (простая структура record'ов), но не роняем сохранение
            throw new IllegalStateException("Не удалось сериализовать маршрут прогулки", e);
        }
    }

    public GeoRoute decode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, GeoRoute.class);
        } catch (JsonProcessingException e) {
            // Битый JSON в БД — логируем и отдаём null, чтобы прогулка всё же открылась
            log.warn("Не удалось разобрать сохранённый маршрут прогулки: {}", e.getMessage());
            return null;
        }
    }
}
