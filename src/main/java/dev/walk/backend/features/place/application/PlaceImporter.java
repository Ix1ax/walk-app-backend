package dev.walk.backend.features.place.application;

import dev.walk.backend.features.city.application.CityService;
import dev.walk.backend.features.city.domain.City;
import dev.walk.backend.features.geo.GeoapifyClient;
import dev.walk.backend.features.geo.domain.GeoPlace;
import dev.walk.backend.features.place.domain.Place;
import dev.walk.backend.features.place.domain.PlaceCategory;
import dev.walk.backend.features.place.domain.PlaceCoverage;
import dev.walk.backend.features.place.repository.PlaceCoverageRepository;
import dev.walk.backend.features.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Ilya Samsonov
 * Импорт мест из Geoapify в отдельной транзакции. Тянет зону один раз большим
 * радиусом и по каждой категории отдельно — чтобы пул был полным по площади и
 * сбалансированным по категориям. Вынесен из {@link PlaceService}, чтобы при гонке
 * (два параллельных запроса в новую зону) откатывалась только вставка-проигравшая,
 * а основной поток мог спокойно перечитать данные из БД
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceImporter {

    /**
     * Радиус, которым реально тянем зону из Geoapify (с запасом над max радиусом эндпоинта)
     */
    private static final int FETCH_RADIUS_METERS = 5000;
    /**
     * Квота мест на каждую категорию Geoapify за проход
     */
    private static final int PER_CATEGORY_LIMIT = 50;
    /**
     * Через сколько считаем зону устаревшей и перезагружаем
     */
    private static final Duration COVERAGE_TTL = Duration.ofDays(30);

    private final PlaceRepository repository;
    private final PlaceCoverageRepository coverageRepository;
    private final GeoapifyClient geoapifyClient;
    private final CityService cityService;

    /**
     * Зона (ячейка) уже загружена и не устарела по TTL
     */
    public boolean isCovered(int cellLat, int cellLon) {
        return coverageRepository.findByCellLatAndCellLon(cellLat, cellLon)
                .map(c -> c.getFetchedAt().isAfter(Instant.now().minus(COVERAGE_TTL)))
                .orElse(false);
    }

    /**
     * Загружает зону из Geoapify: по каждой категории отдельно, дедуп по
     * external_id, сохранение новых, отметка покрытия. cityId резолвится один раз на зону
     */
    @Transactional
    public void importArea(double lat, double lon, int cellLat, int cellLon) {
        Long cityId = cityService.resolveCity(lat, lon).map(City::getId).orElse(null);
        Set<String> seen = new HashSet<>();
        int saved = 0;

        for (String category : PlaceCategoryMapper.geoapifyRequestCategories()) {
            List<GeoPlace> found = geoapifyClient.searchPlaces(
                    lat, lon, FETCH_RADIUS_METERS, List.of(category), PER_CATEGORY_LIMIT);
            for (GeoPlace geo : found) {
                if (geo.externalId() != null && !seen.add(geo.externalId())) {
                    continue; // уже встречали в этом проходе (категории пересекаются)
                }
                if (geo.externalId() != null && repository.existsByExternalId(geo.externalId())) {
                    continue;
                }
                Optional<PlaceCategory> mapped = PlaceCategoryMapper.map(geo.categories());
                if (mapped.isEmpty()) {
                    continue; // категория не из нашего набора
                }
                Place place = new Place();
                place.setCityId(cityId);
                place.setName(geo.name());
                place.setCategory(mapped.get());
                place.setLat(geo.lat());
                place.setLon(geo.lon());
                place.setExternalId(geo.externalId());
                place.setSource("geoapify");
                repository.save(place);
                saved++;
            }
        }

        markCovered(cellLat, cellLon);
        log.info("Импорт зоны cell=({},{}): сохранено {} новых мест (lat={}, lon={})",
                cellLat, cellLon, saved, lat, lon);
    }

    /**
     * Помечает зону загруженной (или освежает fetched_at, если запись уже была)
     */
    private void markCovered(int cellLat, int cellLon) {
        PlaceCoverage coverage = coverageRepository.findByCellLatAndCellLon(cellLat, cellLon)
                .orElseGet(() -> {
                    PlaceCoverage c = new PlaceCoverage();
                    c.setCellLat(cellLat);
                    c.setCellLon(cellLon);
                    return c;
                });
        coverage.setFetchedAt(Instant.now());
        coverageRepository.save(coverage);
    }
}
