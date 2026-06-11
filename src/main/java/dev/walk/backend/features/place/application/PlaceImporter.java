package dev.walk.backend.features.place.application;

import dev.walk.backend.features.city.application.CityService;
import dev.walk.backend.features.city.domain.City;
import dev.walk.backend.features.geo.GeoapifyClient;
import dev.walk.backend.features.geo.domain.GeoPlace;
import dev.walk.backend.features.place.domain.Place;
import dev.walk.backend.features.place.domain.PlaceCategory;
import dev.walk.backend.features.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * @author Ilya Samsonov
 * Импорт мест из Geoapify в отдельной транзакции. Вынесен из {@link PlaceService},
 * чтобы при гонке (два параллельных запроса в новую зону) откатывалась только
 * вставка-проигравшая по уникальному external_id, а основной поток мог спокойно
 * перечитать данные из БД
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceImporter {

    private final PlaceRepository repository;
    private final GeoapifyClient geoapifyClient;
    private final CityService cityService;

    /**
     * Тянет места из Geoapify рядом с точкой и сохраняет новые (дедуп по external_id).
     * Все места рядом со стартом — значит один город, резолвим его один раз на батч
     */
    @Transactional
    public void importNearby(double lat, double lon, int radiusMeters) {
        List<GeoPlace> found = geoapifyClient.searchPlaces(
                lat, lon, radiusMeters, PlaceCategoryMapper.geoapifyRequestCategories());
        if (found.isEmpty()) {
            log.info("Geoapify не вернул мест для lat={}, lon={}, radius={}", lat, lon, radiusMeters);
            return;
        }
        Long cityId = cityService.resolveCity(lat, lon).map(City::getId).orElse(null);
        int saved = 0;
        for (GeoPlace geo : found) {
            if (geo.externalId() != null && repository.existsByExternalId(geo.externalId())) {
                continue;
            }
            Optional<PlaceCategory> category = PlaceCategoryMapper.map(geo.categories());
            if (category.isEmpty()) {
                continue; // категория не из нашего набора — пропускаем
            }
            Place place = new Place();
            place.setCityId(cityId);
            place.setName(geo.name());
            place.setCategory(category.get());
            place.setLat(geo.lat());
            place.setLon(geo.lon());
            place.setExternalId(geo.externalId());
            place.setSource("geoapify");
            repository.save(place);
            saved++;
        }
        log.info("Импорт мест: Geoapify вернул {}, сохранено {} (lat={}, lon={})", found.size(), saved, lat, lon);
    }
}
