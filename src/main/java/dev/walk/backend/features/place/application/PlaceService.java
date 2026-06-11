package dev.walk.backend.features.place.application;

import dev.walk.backend.features.city.application.CityService;
import dev.walk.backend.features.city.domain.City;
import dev.walk.backend.features.geo.GeoapifyClient;
import dev.walk.backend.features.geo.domain.GeoPlace;
import dev.walk.backend.features.place.api.dto.PlaceResponse;
import dev.walk.backend.features.place.domain.Place;
import dev.walk.backend.features.place.domain.PlaceCategory;
import dev.walk.backend.features.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * @author Ilya Samsonov
 */
@Service
@RequiredArgsConstructor
public class PlaceService {

    private final PlaceRepository repository;
    private final GeoapifyClient geoapifyClient;
    private final CityService cityService;

    /**
     * Места рядом со стартом (в радиусе, по близости). Если в БД ничего нет —
     * подтягиваем из Geoapify Places, кэшируем и отдаём уже из базы
     */
    @Transactional
    public List<PlaceResponse> nearby(double lat, double lon, int radiusMeters, int limit) {
        List<Place> places = repository.findNearby(lat, lon, radiusMeters, limit);
        if (places.isEmpty()) {
            importNearby(lat, lon, radiusMeters);
            places = repository.findNearby(lat, lon, radiusMeters, limit);
        }
        return places.stream().map(p -> PlaceResponse.from(p, lat, lon)).toList();
    }

    /**
     * Тянет места из Geoapify рядом с точкой и сохраняет новые (дедуп по external_id).
     * Все места рядом со стартом — значит один город, резолвим его один раз на батч
     */
    private void importNearby(double lat, double lon, int radiusMeters) {
        List<GeoPlace> found = geoapifyClient.searchPlaces(
                lat, lon, radiusMeters, PlaceCategoryMapper.geoapifyRequestCategories());
        if (found.isEmpty()) {
            return;
        }
        Long cityId = cityService.findNearest(lat, lon).map(City::getId).orElse(null);
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
        }
    }
}
