package dev.walk.backend.features.geo;

import com.fasterxml.jackson.databind.JsonNode;

import dev.walk.backend.features.geo.domain.GeoCity;
import dev.walk.backend.features.geo.domain.GeoPlace;
import dev.walk.backend.features.geo.domain.GeoPoint;
import dev.walk.backend.features.geo.domain.GeoRoute;
import dev.walk.backend.features.geo.domain.RouteGeometry;
import dev.walk.backend.features.geo.domain.RouteLeg;
import dev.walk.backend.features.geo.domain.RouteStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

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
    /** Запросы идут через воркер-прокси: ключ держит воркер, мы шлём токен и НЕ светим apiKey */
    private final boolean proxy;

    public GeoapifyClient(GeoapifyProperties properties) {
        this.properties = properties;
        this.proxy = usesProxy(properties);
        // JDK HttpClient, но принудительно HTTP/1.1: дефолтный RestClient берёт HTTP/2,
        // у которого под параллельными запросами всплывает TLS BUFFER_UNDERFLOW.
        // HTTP/1.1 это снимает, при этом клиент потокобезопасный и не договаривается о
        // gzip (чистый JSON). Таймауты держим короткими, но не агрессивными:
        // routing лучше немного подождать, чем часто откатываться на прямые линии
        HttpClient jdkClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory);
        if (proxy) {
            builder.defaultHeader("X-Walkly-Proxy-Token", properties.proxyToken());
        }
        this.http = builder.build();
        log.info("Geoapify client: base={}, proxy={}", properties.baseUrl(), proxy);
    }

    /** Прокси активен, если задан токен и base-url — не сам Geoapify (значит, это воркер) */
    private static boolean usesProxy(GeoapifyProperties p) {
        String base = p.baseUrl() == null ? "" : p.baseUrl();
        return p.proxyToken() != null && !p.proxyToken().isBlank() && !base.contains("api.geoapify.com");
    }

    /** Есть чем авторизоваться: либо прокси-токен, либо прямой apiKey */
    private boolean hasCredentials() {
        return proxy || (properties.apiKey() != null && !properties.apiKey().isBlank());
    }

    /** Добавляет apiKey в URL только при прямом доступе; через воркер ключ подставляет он сам */
    private UriBuilder withApiKey(UriBuilder b) {
        if (!proxy) {
            b.queryParam("apiKey", properties.apiKey());
        }
        return b;
    }

    /**
     * Строит реальный пеший маршрут по улицам через Geoapify Routing API. Геометрия
     * возвращается в GeoJSON-порядке координат: [lon, lat]. При отсутствии ключа,
     * сетевой ошибке или пустом ответе возвращает {@link Optional#empty()}, чтобы
     * генератор прогулки мог упасть на локальную эвристику
     */
    public Optional<GeoRoute> walkingRoute(List<GeoPoint> waypoints) {
        if (!hasCredentials()) {
            log.warn("Geoapify не настроен (нет ключа/прокси) — routing пропущен");
            return Optional.empty();
        }
        if (waypoints == null || waypoints.size() < 2) {
            return Optional.empty();
        }
        try {
            JsonNode body = http.get()
                    .uri(uri -> withApiKey(uri.path("/v1/routing")
                            .queryParam("waypoints", routeWaypoints(waypoints))
                            .queryParam("mode", "walk")
                            .queryParam("type", "short")
                            .queryParam("format", "geojson")
                            .queryParam("lang", "ru")).build())
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode features = body == null ? null : body.get("features");
            if (features == null || !features.isArray() || features.isEmpty()) {
                return Optional.empty();
            }

            JsonNode feature = features.get(0);
            JsonNode props = feature.get("properties");
            JsonNode geometry = feature.get("geometry");
            if (props == null || geometry == null || geometry.path("coordinates").isMissingNode()) {
                return Optional.empty();
            }

            List<RouteLeg> legs = parseRouteLegs(props);
            if (legs.size() != waypoints.size() - 1) {
                log.warn("Geoapify routing вернул {} legs для {} waypoints — используем fallback",
                        legs.size(), waypoints.size());
                return Optional.empty();
            }

            long distanceMeters = Math.round(props.path("distance").asDouble(sumLegDistances(legs)));
            long timeSeconds = Math.round(props.path("time").asDouble(sumLegSeconds(legs)));
            return Optional.of(new GeoRoute(
                    parseRouteGeometry(geometry),
                    distanceMeters,
                    timeSeconds,
                    legs,
                    false));
        } catch (Exception e) {
            log.warn("Geoapify routing не удался: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Возвращает город (имя, страна, центр) по координатам. При отсутствии ключа,
     * ошибке сети или пустом ответе — {@link Optional#empty()}
     */
    public Optional<GeoCity> reverseCity(double lat, double lon) {
        if (!hasCredentials()) {
            log.warn("Geoapify не настроен (нет ключа/прокси) — reverse geocoding пропущен");
            return Optional.empty();
        }
        try {
            JsonNode body = http.get()
                    .uri(uri -> withApiKey(uri.path("/v1/geocode/reverse")
                            .queryParam("lat", lat)
                            .queryParam("lon", lon)
                            .queryParam("type", "city")
                            .queryParam("lang", "ru")
                            .queryParam("format", "json")).build())
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
        if (!hasCredentials()) {
            log.warn("Geoapify не настроен (нет ключа/прокси) — поиск города пропущен");
            return Optional.empty();
        }
        try {
            JsonNode body = http.get()
                    .uri(uri -> withApiKey(uri.path("/v1/geocode/search")
                            .queryParam("text", query)
                            .queryParam("type", "city")
                            .queryParam("format", "json")
                            .queryParam("lang", "ru")
                            .queryParam("bias", "countrycode:ru")
                            .queryParam("limit", 1)).build())
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
        if (!hasCredentials()) {
            log.warn("Geoapify не настроен (нет ключа/прокси) — поиск мест пропущен");
            return List.of();
        }
        try {
            JsonNode body = http.get()
                    .uri(uri -> withApiKey(uri.path("/v2/places")
                            .queryParam("categories", String.join(",", categories))
                            .queryParam("filter", "circle:" + lon + "," + lat + "," + radiusMeters)
                            .queryParam("bias", "proximity:" + lon + "," + lat)
                            .queryParam("lang", "ru")
                            .queryParam("limit", limit)).build())
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

    private static String routeWaypoints(List<GeoPoint> waypoints) {
        List<String> parts = new ArrayList<>(waypoints.size());
        for (GeoPoint p : waypoints) {
            parts.add(p.lat() + "," + p.lon());
        }
        return String.join("|", parts);
    }

    private static RouteGeometry parseRouteGeometry(JsonNode geometry) {
        String type = geometry.path("type").asText("MultiLineString");
        JsonNode coordinates = geometry.path("coordinates");
        List<List<List<Double>>> lines = new ArrayList<>();
        if ("LineString".equals(type)) {
            lines.add(parseLine(coordinates));
        } else {
            for (JsonNode line : coordinates) {
                lines.add(parseLine(line));
            }
        }
        return new RouteGeometry("MultiLineString", lines);
    }

    private static List<List<Double>> parseLine(JsonNode line) {
        List<List<Double>> points = new ArrayList<>();
        for (JsonNode point : line) {
            if (point.isArray() && point.size() >= 2) {
                points.add(List.of(point.get(0).asDouble(), point.get(1).asDouble()));
            }
        }
        return points;
    }

    private static List<RouteLeg> parseRouteLegs(JsonNode props) {
        JsonNode legs = props.get("legs");
        if (legs == null || !legs.isArray() || legs.isEmpty()) {
            return List.of(new RouteLeg(
                    Math.round(props.path("distance").asDouble(0)),
                    Math.round(props.path("time").asDouble(0)),
                    List.of()));
        }
        List<RouteLeg> result = new ArrayList<>(legs.size());
        for (JsonNode leg : legs) {
            result.add(new RouteLeg(
                    Math.round(leg.path("distance").asDouble(0)),
                    Math.round(leg.path("time").asDouble(0)),
                    parseRouteSteps(leg)));
        }
        return result;
    }

    private static List<RouteStep> parseRouteSteps(JsonNode leg) {
        JsonNode steps = leg.get("steps");
        if (steps == null || !steps.isArray() || steps.isEmpty()) {
            return List.of();
        }
        List<RouteStep> result = new ArrayList<>(steps.size());
        for (JsonNode step : steps) {
            result.add(new RouteStep(
                    Math.round(step.path("distance").asDouble(0)),
                    Math.round(step.path("time").asDouble(0)),
                    instructionText(step)));
        }
        return result;
    }

    private static String instructionText(JsonNode step) {
        JsonNode instruction = step.get("instruction");
        if (instruction == null || instruction.isNull()) {
            return null;
        }
        if (instruction.isTextual()) {
            return instruction.asText();
        }
        return text(instruction, "text");
    }

    private static long sumLegDistances(List<RouteLeg> legs) {
        long total = 0;
        for (RouteLeg leg : legs) {
            total += leg.distanceMeters();
        }
        return total;
    }

    private static long sumLegSeconds(List<RouteLeg> legs) {
        long total = 0;
        for (RouteLeg leg : legs) {
            total += leg.timeSeconds();
        }
        return total;
    }
}
