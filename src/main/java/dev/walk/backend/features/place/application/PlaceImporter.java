package dev.walk.backend.features.place.application;

import dev.walk.backend.features.city.application.CityService;
import dev.walk.backend.features.city.domain.City;
import dev.walk.backend.features.place.application.source.PlaceSource;
import dev.walk.backend.features.place.application.source.SourcePlace;
import dev.walk.backend.features.place.domain.Place;
import dev.walk.backend.features.place.domain.PlaceCoverage;
import dev.walk.backend.features.place.repository.PlaceCoverageRepository;
import dev.walk.backend.features.place.repository.PlaceRepository;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Ilya Samsonov
 * Импорт мест из активного источника ({@link PlaceSource}, по умолчанию OSM/Overpass)
 * в отдельной транзакции. Тянет зону один раз большим радиусом, дедуп по external_id,
 * сохраняет новое. Вынесен из {@link PlaceService}, чтобы при гонке (два параллельных
 * запроса в новую зону) откатывалась только вставка-проигравшая, а основной поток мог
 * спокойно перечитать данные из БД
 */
@Slf4j
@Component
public class PlaceImporter {

    /**
     * Радиус, которым реально тянем зону (с запасом над max радиусом эндпоинта).
     * 3 км: публичный Overpass на 5 км по всем категориям слишком долго отвечает
     */
    private static final int FETCH_RADIUS_METERS = 3000;
    /**
     * Через сколько считаем зону устаревшей и перезагружаем
     */
    private static final Duration COVERAGE_TTL = Duration.ofDays(30);

    private final PlaceRepository repository;
    private final PlaceCoverageRepository coverageRepository;
    private final CityService cityService;
    private final PlaceSource source;

    public PlaceImporter(PlaceRepository repository,
                         PlaceCoverageRepository coverageRepository,
                         CityService cityService,
                         List<PlaceSource> sources,
                         @Value("${walk.places.source:osm}") String sourceId) {
        this.repository = repository;
        this.coverageRepository = coverageRepository;
        this.cityService = cityService;
        this.source = sources.stream()
                .filter(s -> s.id().equalsIgnoreCase(sourceId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Источник мест '" + sourceId + "' не найден. Доступны: "
                                + sources.stream().map(PlaceSource::id).toList()));
        log.info("Источник мест: {}", this.source.id());
    }

    /**
     * Зона (ячейка) уже загружена и не устарела по TTL
     */
    public boolean isCovered(int cellLat, int cellLon) {
        return coverageRepository.findByCellLatAndCellLon(cellLat, cellLon)
                .map(c -> c.getFetchedAt().isAfter(Instant.now().minus(COVERAGE_TTL)))
                .orElse(false);
    }

    /**
     * Загружает зону из активного источника: дедуп по external_id, сохранение новых,
     * отметка покрытия. cityId резолвится один раз на зону
     */
    @Transactional
    public void importArea(double lat, double lon, int cellLat, int cellLon) {
        Instant now = Instant.now();
        Long cityId = cityService.resolveCity(lat, lon).map(City::getId).orElse(null);
        Set<String> seen = new HashSet<>();
        int saved = 0;

        for (SourcePlace sp : source.fetchArea(lat, lon, FETCH_RADIUS_METERS)) {
            if (sp.externalId() != null && !seen.add(sp.externalId())) {
                continue; // дубль в пределах одного прохода
            }
            if (sp.externalId() != null && repository.existsByExternalId(sp.externalId())) {
                repository.touchLastSeen(sp.externalId(), now); // место ещё живо — освежаем
                continue;
            }
            Place place = new Place();
            place.setCityId(cityId);
            place.setName(sp.name());
            place.setCategory(sp.category());
            place.setLat(sp.lat());
            place.setLon(sp.lon());
            place.setExternalId(sp.externalId());
            place.setSource(source.id());
            place.setNotable(sp.notable());
            place.setLastSeenAt(now);
            repository.save(place);
            saved++;
        }

        markCovered(cellLat, cellLon);
        log.info("Импорт зоны cell=({},{}) [{}]: сохранено {} новых мест (lat={}, lon={})",
                cellLat, cellLon, source.id(), saved, lat, lon);
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
