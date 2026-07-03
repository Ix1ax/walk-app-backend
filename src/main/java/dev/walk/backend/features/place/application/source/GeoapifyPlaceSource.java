package dev.walk.backend.features.place.application.source;

import dev.walk.backend.features.geo.GeoapifyClient;
import dev.walk.backend.features.geo.domain.GeoPlace;
import dev.walk.backend.features.place.application.PlaceCategoryMapper;
import dev.walk.backend.features.place.domain.PlaceCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author Ilya Samsonov
 * Источник мест на Geoapify Places (платный/квотируемый). Тянет по каждой категории
 * отдельно с квотой — чтобы пул был сбалансирован по типам. Оставлен как альтернатива
 * OSM; активируется конфигом {@code walk.places.source=geoapify}. Запросы по категориям
 * идут ПАРАЛЛЕЛЬНО: они независимы, а последовательно 6 × ~1с давали заметную задержку
 * импорта зоны прямо в теле запроса
 */
@Component
@RequiredArgsConstructor
public class GeoapifyPlaceSource implements PlaceSource {

    /**
     * Квота мест на каждую категорию Geoapify за проход
     */
    private static final int PER_CATEGORY_LIMIT = 50;

    private final GeoapifyClient client;

    /**
     * Пул под параллельные запросы по категориям. Размер = числу категорий, чтобы все
     * стартовали разом; потоки-демоны, чтобы не держать остановку приложения. Не грузим
     * общий ForkJoinPool (там блокирующий IO — антипаттерн)
     */
    private final ExecutorService pool = Executors.newFixedThreadPool(
            Math.max(1, PlaceCategoryMapper.geoapifyRequestCategories().size()),
            r -> {
                Thread t = new Thread(r, "geoapify-places");
                t.setDaemon(true);
                return t;
            });

    @Override
    public String id() {
        return "geoapify";
    }

    @Override
    public List<SourcePlace> fetchArea(double lat, double lon, int radiusMeters) {
        // Каждую категорию запрашиваем параллельно; порядок сохраняем (для стабильного
        // дедупа), результаты сливаем уже в одном потоке
        List<CompletableFuture<List<GeoPlace>>> futures = PlaceCategoryMapper.geoapifyRequestCategories().stream()
                .map(category -> CompletableFuture.supplyAsync(
                        () -> client.searchPlaces(lat, lon, radiusMeters, List.of(category), PER_CATEGORY_LIMIT),
                        pool))
                .toList();

        List<SourcePlace> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CompletableFuture<List<GeoPlace>> future : futures) {
            for (GeoPlace geo : future.join()) {
                if (geo.externalId() != null && !seen.add(geo.externalId())) {
                    continue; // уже встречали в этом проходе (категории пересекаются)
                }
                Optional<PlaceCategory> mapped = PlaceCategoryMapper.map(geo.categories());
                if (mapped.isEmpty()) {
                    continue;
                }
                PlaceCategory cat = mapped.get();
                boolean notable = cat == PlaceCategory.MUSEUM || cat == PlaceCategory.RELIGIOUS
                        || cat == PlaceCategory.LANDMARK || cat == PlaceCategory.VIEWPOINT;
                result.add(new SourcePlace(
                        geo.externalId(), geo.name(), cat, geo.lat(), geo.lon(), notable));
            }
        }
        return result;
    }
}
