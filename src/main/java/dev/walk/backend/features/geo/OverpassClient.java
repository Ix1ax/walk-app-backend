package dev.walk.backend.features.geo;

import com.fasterxml.jackson.databind.JsonNode;
import dev.walk.backend.features.geo.domain.OsmPlace;
import dev.walk.backend.features.place.application.OsmCategoryMapper;
import dev.walk.backend.features.place.application.OsmCategoryMapper.OsmFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Ilya Samsonov
 * Клиент к Overpass API (OpenStreetMap). Одним запросом тянет POI зоны по нашему
 * набору тег-фильтров ({@link OsmCategoryMapper}); ways/relations отдаём с центром
 * ({@code out center}). Бесплатно и без ключа. При ошибке/пустом ответе — пустой список
 */
@Slf4j
@Component
public class OverpassClient {

    /**
     * Ключи тегов, которые нас интересуют: для классификации (amenity…highway) и для
     * признака значимости (wikidata/wikipedia/heritage). Из них собираем «k=v»
     */
    private static final List<String> RELEVANT_KEYS =
            List.of("amenity", "tourism", "leisure", "historic", "place", "highway",
                    "wikidata", "wikipedia", "heritage");

    private final OverpassProperties properties;
    private final RestClient http;

    public OverpassClient(OverpassProperties properties) {
        this.properties = properties;
        // JDK HttpClient на HTTP/1.1 + таймауты (как в других гео-клиентах). Read-таймаут
        // с запасом над серверным timeout Overpass, чтобы дождаться тяжёлой зоны
        HttpClient jdkClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkClient);
        // Запас над серверным timeout: публичный Overpass ещё и держит запрос в очереди
        factory.setReadTimeout(Duration.ofSeconds(properties.timeoutSecondsOrDefault() + 30L));
        this.http = RestClient.builder()
                // Overpass требует осмысленный User-Agent, иначе 406 Not Acceptable
                .defaultHeader("User-Agent", "walk-backend/0.1 (https://walk.dev; contact@walk.dev)")
                .requestFactory(factory)
                .build();
    }

    /**
     * Тянет POI в радиусе {@code radiusMeters} от точки по тег-фильтрам. Возвращает
     * только именованные объекты с координатами
     */
    public List<OsmPlace> fetchArea(double lat, double lon, int radiusMeters) {
        String query = buildQuery(lat, lon, radiusMeters);
        try {
            JsonNode body = http.post()
                    .uri(properties.url())
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(query)
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode elements = body == null ? null : body.get("elements");
            if (elements == null || !elements.isArray()) {
                return List.of();
            }

            List<OsmPlace> places = new ArrayList<>();
            for (JsonNode el : elements) {
                OsmPlace place = parse(el);
                if (place != null) {
                    places.add(place);
                }
            }
            return places;
        } catch (Exception e) {
            log.warn("Overpass запрос не удался: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Строит Overpass QL: union по всем тег-фильтрам в радиусе, вывод с центром и тегами
     */
    private String buildQuery(double lat, double lon, int radiusMeters) {
        StringBuilder sb = new StringBuilder();
        sb.append("[out:json][timeout:").append(properties.timeoutSecondsOrDefault()).append("];\n(\n");
        String around = "(around:" + radiusMeters + "," + lat + "," + lon + ")";
        for (OsmFilter f : OsmCategoryMapper.queryFilters()) {
            sb.append("  nwr[\"").append(f.key()).append("\"=\"").append(f.value()).append("\"]");
            if (f.requireName()) {
                sb.append("[\"name\"]");
            }
            sb.append(around).append(";\n");
        }
        sb.append(");\nout center tags;");
        return sb.toString();
    }

    /**
     * Разбирает элемент Overpass: координаты (node — lat/lon, way/relation — center),
     * имя и интересующие теги. {@code null}, если нет имени или координат
     */
    private OsmPlace parse(JsonNode el) {
        JsonNode tags = el.get("tags");
        if (tags == null) {
            return null;
        }
        String name = text(tags, "name");
        if (name == null || isJunkName(name)) {
            return null; // без имени или с мусорным именем точка бесполезна
        }

        double lat;
        double lon;
        if (el.hasNonNull("lat") && el.hasNonNull("lon")) {
            lat = el.get("lat").asDouble();
            lon = el.get("lon").asDouble();
        } else if (el.has("center")) {
            JsonNode c = el.get("center");
            lat = c.path("lat").asDouble();
            lon = c.path("lon").asDouble();
        } else {
            return null; // нет координат
        }

        List<String> kv = new ArrayList<>();
        for (String key : RELEVANT_KEYS) {
            String value = text(tags, key);
            if (value != null) {
                kv.add(key + "=" + value);
            }
        }

        String externalId = el.path("type").asText("node") + "/" + el.path("id").asLong();
        return new OsmPlace(externalId, name, lat, lon, kv);
    }

    /**
     * Мусорное имя: слишком короткое или без единой буквы (число/символы/эмодзи) —
     * для прогулки такое место бесполезно
     */
    private static boolean isJunkName(String name) {
        String n = name.trim();
        if (n.length() < 3) {
            return true;
        }
        return !n.chars().anyMatch(Character::isLetter);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.asText().isBlank()) {
            return null;
        }
        return v.asText();
    }
}
