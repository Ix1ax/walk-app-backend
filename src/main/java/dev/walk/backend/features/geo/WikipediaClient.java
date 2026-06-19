package dev.walk.backend.features.geo;

import com.fasterxml.jackson.databind.JsonNode;
import dev.walk.backend.features.geo.domain.WikiSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Ilya Samsonov
 * Клиент к русской Wikipedia для обогащения мест фото и описанием. Сначала ищет
 * статью рядом по координатам (geosearch), сверяет её заголовок с названием места,
 * затем берёт краткую выжимку (REST summary) — оттуда и описание, и превью-фото.
 * Покрытие частичное: у незначимых точек (кафе и т.п.) статьи обычно нет → empty
 */
@Slf4j
@Component
public class WikipediaClient {

    /**
     * Радиус гео-поиска статьи вокруг места, м
     */
    private static final int GEOSEARCH_RADIUS_METERS = 300;
    /**
     * Максимальная длина описания в ответе (обрезаем длинные выжимки)
     */
    private static final int MAX_DESCRIPTION_CHARS = 400;
    /**
     * Минимальная длина значимого слова при сопоставлении названия и заголовка
     */
    private static final int MIN_TOKEN_LENGTH = 4;
    /**
     * Родовые слова мест: их игнорируем при сопоставлении, иначе «храм X» ложно
     * совпадёт с «храмом Y» по слову «храм». Совпадать должны собственные имена
     */
    private static final Set<String> GENERIC_WORDS = Set.of(
            "храм", "собор", "церковь", "часовня", "монастырь", "музей", "галерея",
            "памятник", "монумент", "сквер", "парк", "площадь", "улица", "проспект",
            "бульвар", "набережная", "святого", "святой", "князя", "имени", "центр",
            "дворец", "культуры", "театр", "библиотека", "усадьба", "дом");

    private final RestClient http;

    public WikipediaClient() {
        // Тот же приём, что и в GeoapifyClient: JDK HttpClient на HTTP/1.1 + таймауты,
        // чтобы под параллельными запросами не ловить TLS-сбои и не висеть
        HttpClient jdkClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdkClient);
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.http = RestClient.builder()
                .baseUrl("https://ru.wikipedia.org")
                // Wikimedia требует осмысленный User-Agent, иначе 403
                .defaultHeader("User-Agent", "walk-backend/0.1 (https://walk.dev; contact@walk.dev)")
                .requestFactory(factory)
                .build();
    }

    /**
     * Ищет статью Wikipedia про место по его названию и координатам. {@code cityName}
     * (может быть null) исключается из сопоставления — иначе любой POI совпал бы с
     * городскими статьями по слову-названию города. Возвращает {@link Optional#empty()}
     * при сетевой ошибке или если подходящей статьи нет
     */
    public Optional<WikiSummary> lookup(String name, String cityName, double lat, double lon) {
        try {
            String title = findArticleTitle(name, cityName, lat, lon);
            if (title == null) {
                return Optional.empty();
            }
            return fetchSummary(title);
        } catch (Exception e) {
            log.warn("Wikipedia обогащение не удалось для '{}': {}", name, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Гео-поиск статей рядом и выбор той, чей заголовок совпадает с названием места
     * по значимым словам. Имя города из сопоставления исключаем. Чисто по близости
     * статью НЕ берём — это плодит ложные привязки (указатель рядом с собором получал
     * бы статью собора). Лучше null, чем чужое фото
     */
    private String findArticleTitle(String name, String cityName, double lat, double lon) {
        JsonNode body = http.get()
                .uri(uri -> uri.path("/w/api.php")
                        .queryParam("action", "query")
                        .queryParam("list", "geosearch")
                        .queryParam("gscoord", lat + "|" + lon)
                        .queryParam("gsradius", GEOSEARCH_RADIUS_METERS)
                        .queryParam("gslimit", 5)
                        .queryParam("format", "json")
                        .build())
                .retrieve()
                .body(JsonNode.class);

        JsonNode results = body == null ? null : body.path("query").path("geosearch");
        if (results == null || !results.isArray() || results.isEmpty()) {
            return null;
        }

        Set<String> cityTokens = cityName == null ? Set.of() : significantTokens(cityName);
        Set<String> nameTokens = significantTokens(name);
        nameTokens.removeAll(cityTokens);
        if (nameTokens.isEmpty()) {
            return null; // у названия нет собственных слов (кроме города) — не на что опереться
        }
        // results отсортированы по близости — возвращаем первый подходящий по словам
        for (JsonNode r : results) {
            String title = r.path("title").asText(null);
            if (title == null) {
                continue;
            }
            Set<String> titleTokens = significantTokens(title);
            titleTokens.removeAll(cityTokens);
            if (titleTokens.stream().anyMatch(nameTokens::contains)) {
                return title;
            }
        }
        return null;
    }

    /**
     * Значимые слова строки: нормализованные, длиной от {@link #MIN_TOKEN_LENGTH}
     * и не родовые ({@link #GENERIC_WORDS})
     */
    private static Set<String> significantTokens(String s) {
        return Arrays.stream(normalize(s).split(" "))
                .filter(t -> t.length() >= MIN_TOKEN_LENGTH)
                .filter(t -> !GENERIC_WORDS.contains(t))
                .collect(Collectors.toSet());
    }

    /**
     * Краткая выжимка статьи по заголовку (описание + превью-фото + ссылка)
     */
    private Optional<WikiSummary> fetchSummary(String title) {
        JsonNode body = http.get()
                .uri(uri -> uri.path("/api/rest_v1/page/summary/{title}").build(title))
                .retrieve()
                .body(JsonNode.class);
        if (body == null) {
            return Optional.empty();
        }
        String extract = trim(text(body, "extract"));
        String thumb = body.path("thumbnail").path("source").asText(null);
        String page = body.path("content_urls").path("desktop").path("page").asText(null);
        if (extract == null && thumb == null) {
            return Optional.empty(); // ни текста, ни фото — обогащать нечем
        }
        return Optional.of(new WikiSummary(title, extract, blankToNull(thumb), blankToNull(page)));
    }

    /**
     * Нормализует строку для нечёткого сравнения: нижний регистр, без диакритики,
     * кавычек и лишних пробелов
     */
    private static String normalize(String s) {
        return Normalizer.normalize(s.toLowerCase(), Normalizer.Form.NFKD)
                .replaceAll("\\(.*?\\)", " ")          // дизамбигуатор «(Тверь)» — не часть имени
                .replaceAll("[\\p{M}«»\"'`.,]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        if (s.length() <= MAX_DESCRIPTION_CHARS) {
            return s;
        }
        return s.substring(0, MAX_DESCRIPTION_CHARS).trim() + "…";
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asText();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
