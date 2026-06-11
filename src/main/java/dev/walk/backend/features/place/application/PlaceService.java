package dev.walk.backend.features.place.application;

import dev.walk.backend.features.place.api.dto.PlaceResponse;
import dev.walk.backend.features.place.domain.Place;
import dev.walk.backend.features.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author Ilya Samsonov
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceService {

    private final PlaceRepository repository;
    private final PlaceImporter importer;

    /**
     * Места рядом со стартом (в радиусе, по близости). Если в БД ничего нет —
     * подтягиваем из Geoapify Places, кэшируем и отдаём уже из базы
     */
    public List<PlaceResponse> nearby(double lat, double lon, int radiusMeters, int limit) {
        List<Place> places = repository.findNearby(lat, lon, radiusMeters, limit);
        if (places.isEmpty()) {
            try {
                importer.importNearby(lat, lon, radiusMeters);
            } catch (DataIntegrityViolationException e) {
                // Гонка: параллельный запрос уже импортировал эту зону — просто перечитаем
                log.info("Места уже импортированы параллельным запросом — читаем из БД (lat={}, lon={})", lat, lon);
            }
            places = repository.findNearby(lat, lon, radiusMeters, limit);
        }
        return places.stream().map(p -> PlaceResponse.from(p, lat, lon)).toList();
    }
}
