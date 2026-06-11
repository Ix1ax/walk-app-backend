package dev.walk.backend.features.city.application;

import dev.walk.backend.features.city.domain.City;
import dev.walk.backend.features.city.repository.CityRepository;
import dev.walk.backend.features.city.api.dto.CityResponse;
import dev.walk.backend.common.Slugs;
import dev.walk.backend.common.exception.NotFoundException;
import dev.walk.backend.features.geo.GeoapifyClient;
import dev.walk.backend.features.geo.domain.GeoCity;
import dev.walk.backend.features.geo.domain.GeoDistance;
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
public class CityService {

    /**
     * Максимальное расстояние до центра города, при котором считаем, что
     * пользователь "в нём"
     */
    private static final double MAX_NEAREST_DISTANCE_METERS = 100_000;

    private final CityRepository repository;
    private final GeoapifyClient geoapifyClient;

    /**
     * Поиск городов по части названия. Пустой запрос — весь список.
     * Если в БД ничего не нашлось — ищем в Geoapify, найденный город сохраняем
     * в БД, и дальше он отдаётся уже из базы
     */
    @Transactional
    public List<CityResponse> search(String query) {
        if (query == null || query.isBlank()) {
            return repository.findAllByOrderByNameAsc().stream().map(CityResponse::from).toList();
        }

        String q = query.trim();
        List<City> cities = repository.findByNameContainingIgnoreCaseOrSlugContainingIgnoreCaseOrderByNameAsc(q, q);
        if (!cities.isEmpty()) {
            return cities.stream().map(CityResponse::from).toList();
        }

        // В БД нет — пробуем геокодер и сохраняем найденное
        return geoapifyClient.searchCity(q)
                .map(this::importCity)
                .map(CityResponse::from)
                .map(List::of)
                .orElseGet(List::of);
    }

    /**
     * Сохраняет город из геокодера (или возвращает уже существующий с таким именем)
     */
    private City importCity(GeoCity geo) {
        return repository.findByNameIgnoreCase(geo.name())
                .orElseGet(() -> {
                    City city = new City();
                    city.setName(geo.name());
                    city.setSlug(uniqueSlug(geo.name()));
                    city.setCountryCode(geo.countryCode() == null ? "RU" : geo.countryCode().toUpperCase());
                    city.setCenterLat(geo.lat());
                    city.setCenterLon(geo.lon());
                    return repository.save(city);
                });
    }

    /**
     * slug из названия
     * при коллизии добавляет суффикс -2, -3 и т.д
     */
    private String uniqueSlug(String name) {
        String base = Slugs.slugify(name);
        if (base.isBlank()) {
            base = "city";
        }
        String slug = base;
        int counter = 2;
        while (repository.findBySlug(slug).isPresent()) {
            slug = base + "-" + counter++;
        }
        return slug;
    }

    /**
     * Определяет текущий город по координатам: сначала через reverse geocoding
     * (Geoapify), и если такого города у нас нет — ближайший из поддерживаемых
     */
    public CityResponse current(double lat, double lon) {
        Optional<City> byName = geoapifyClient.reverseCity(lat, lon)
                .flatMap(repository::findByNameIgnoreCase);
        if (byName.isPresent()) {
            return CityResponse.from(byName.get());
        }
        return findNearest(lat, lon)
                .map(CityResponse::from)
                .orElseThrow(() -> new NotFoundException("Город пока не поддерживается"));
    }

    /**
     * Ближайший к точке поддерживаемый город (в пределах
     * {@link #MAX_NEAREST_DISTANCE_METERS}). {@link Optional#empty()}, если такого нет.
     * Используется и для определения текущего города, и для привязки мест к городу
     */
    public Optional<City> findNearest(double lat, double lon) {
        City nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (City city : repository.findAll()) {
            double distance = GeoDistance.haversineMeters(lat, lon, city.getCenterLat(), city.getCenterLon());
            if (distance < bestDistance) {
                bestDistance = distance;
                nearest = city;
            }
        }
        if (nearest != null && bestDistance <= MAX_NEAREST_DISTANCE_METERS) {
            return Optional.of(nearest);
        }
        return Optional.empty();
    }
}
