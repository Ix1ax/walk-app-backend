package dev.walk.backend.features.geo;

import com.fasterxml.jackson.databind.JsonNode;

import dev.walk.backend.features.geo.domain.GeoCity;
import dev.walk.backend.features.geo.domain.GeoPlace;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Ilya Samsonov
 *         Клиент к Geoapify
 */
@Slf4j
@Component
public class GeoapifyClient {

    private final GeoapifyProperties properties;
    private final RestClient http;

    public GeoapifyClient(GeoapifyProperties properties) {
        this.properties = properties;
        // JDK HttpClient, но принудительно HTTP/1.1: дефолтный RestClient берёт HTTP/2,
        // у которого под параллельными запросами всплывает TLS BUFFER_UNDERFLOW.
        // HTTP/1.1 это снимает, при этом клиент потокобезопасный и не договаривается о
        // gzip (чистый JSON). Таймауты — чтобы запрос не висел, а падал предсказуемо
        HttpClient jdkClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(Duration.ofSeconds(6));
        this.http = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * Возвращает город (имя, страна, центр) по координатам. При отсутствии ключа,
     * ошибке сети или пустом ответе — {@link Optional#empty()}
     */
    public Optional<GeoCity> reverseCity(double lat, double lon) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("Geoapify API key не задан — reverse geocoding пропущен");
            return Optional.empty();
        }
        try {
            JsonNode body = http.get()
                    .uri(uri -> uri.path("/v1/geocode/reverse")
                            .queryParam("lat", lat)
                            .queryParam("lon", lon)
                            .queryParam("type", "city")
                            .queryParam("lang", "ru")
                            .queryParam("format", "json")
                            .queryParam("apiKey", properties.apiKey())
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            if (body == null) {
                return Optional.empty();
            }
            JsonNode results = body.get("results");
            if (results == null || !results.isArray() || results.isEmpty()) {
                return Optional.empty();
            }
            JsonNode r = results.get(0);
            String name = text(r, "city");
            if (name == null || !r.hasNonNull("lat") || !r.hasNonNull("lon")) {
                return Optional.empty();
            }
            return Optional.of(new GeoCity(
                    name,
                    text(r, "country_code"),
                    r.get("lat").asDouble(),
                    r.get("lon").asDouble()));
        } catch (Exception e) {
            log.warn("Geoapify reverse geocoding не удался: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Ищет город по тексту (forward geocoding). Берёт лучший результат, с уклоном
     * в сторону России. Возвращает {@link Optional#empty()} при отсутствии ключа,
     * ошибке или если ничего не найдено
     */
    public Optional<GeoCity> searchCity(String query) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("Geoapify API key не задан — поиск города пропущен");
            return Optional.empty();
        }
        try {
            JsonNode body = http.get()
                    .uri(uri -> uri.path("/v1/geocode/search")
                            .queryParam("text", query)
                            .queryParam("type", "city")
                            .queryParam("format", "json")
                            .queryParam("lang", "ru")
                            .queryParam("bias", "countrycode:ru")
                            .queryParam("limit", 1)
                            .queryParam("apiKey", properties.apiKey())
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            if (body == null) {
                return Optional.empty();
            }
            JsonNode results = body.get("results");
            if (results == null || !results.isArray() || results.isEmpty()) {
                return Optional.empty();
            }
            JsonNode r = results.get(0);

            String name = text(r, "city");
            if (name == null) {
                name = text(r, "name");
            }
            if (name == null) {
                name = text(r, "formatted");
            }
            if (name == null || !r.hasNonNull("lat") || !r.hasNonNull("lon")) {
                return Optional.empty();
            }
            return Optional.of(new GeoCity(
                    name,
                    text(r, "country_code"),
                    r.get("lat").asDouble(),
                    r.get("lon").asDouble()));
        } catch (Exception e) {
            log.warn("Geoapify поиск города не удался: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Ищет места в радиусе {@code radiusMeters} от точки по заданным категориям
     * Geoapify, не больше {@code limit} штук. Возвращает пустой список при отсутствии
     * ключа, ошибке или если ничего не найдено
     */
    public List<GeoPlace> searchPlaces(double lat, double lon, int radiusMeters, List<String> categories, int limit) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("Geoapify API key не задан — поиск мест пропущен");
            return List.of();
        }
        try {
            JsonNode body = http.get()
                    .uri(uri -> uri.path("/v2/places")
                            .queryParam("categories", String.join(",", categories))
                            .queryParam("filter", "circle:" + lon + "," + lat + "," + radiusMeters)
                            .queryParam("bias", "proximity:" + lon + "," + lat)
                            .queryParam("lang", "ru")
                            .queryParam("limit", limit)
                            .queryParam("apiKey", properties.apiKey())
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            if (body == null) {
                return List.of();
            }
            JsonNode features = body.get("features");
            if (features == null || !features.isArray() || features.isEmpty()) {
                return List.of();
            }

            List<GeoPlace> places = new ArrayList<>();
            for (JsonNode feature : features) {
                JsonNode props = feature.get("properties");
                if (props == null) {
                    continue;
                }
                String name = text(props, "name");
                if (name == null || !props.hasNonNull("lat") || !props.hasNonNull("lon")) {
                    continue; // без имени или координат место бесполезно
                }
                if (isClosed(props)) {
                    continue; // в OSM помечено как закрытое/нежилое — не берём
                }
                List<String> categoryNames = new ArrayList<>();
                JsonNode cats = props.get("categories");
                if (cats != null && cats.isArray()) {
                    cats.forEach(c -> categoryNames.add(c.asText()));
                }
                places.add(new GeoPlace(
                        text(props, "place_id"),
                        name,
                        categoryNames,
                        props.get("lat").asDouble(),
                        props.get("lon").asDouble()));
            }
            return places;
        } catch (Exception e) {
            log.warn("Geoapify поиск мест не удался: {}", e.getMessage());
            return List.of();
        }
    }

    /** Lifecycle-приставки OSM, означающие, что объект больше не действует */
    private static final List<String> CLOSED_PREFIXES = List.of(
            "disused:", "abandoned:", "was:", "removed:", "demolished:", "razed:", "destroyed:");

    /**
     * Помечено ли место в исходных данных OSM как закрытое/нежилое.
     * Смотрим сырые теги Geoapify (`datasource.raw`): lifecycle-приставки и opening_hours=closed/off
     */
    private static boolean isClosed(JsonNode props) {
        JsonNode datasource = props.get("datasource");
        JsonNode raw = datasource == null ? null : datasource.get("raw");
        if (raw == null || !raw.isObject()) {
            return false;
        }
        var names = raw.fieldNames();
        while (names.hasNext()) {
            String key = names.next().toLowerCase();
            if (key.equals("disused") || key.equals("abandoned")) {
                return true;
            }
            for (String prefix : CLOSED_PREFIXES) {
                if (key.startsWith(prefix)) {
                    return true;
                }
            }
        }
        JsonNode hours = raw.get("opening_hours");
        if (hours != null && !hours.isNull()) {
            String v = hours.asText().toLowerCase().trim();
            return v.equals("closed") || v.equals("off");
        }
        return false;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }
}
