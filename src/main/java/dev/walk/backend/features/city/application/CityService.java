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
     * Сохраняет город из геокодера (или возвращает уже существующий с таким именем).
     * Имя чистится от административных приставок: «городской округ Тверь» → «Тверь»
     */
    private City importCity(GeoCity geo) {
        String name = cleanCityName(geo.name());
        return repository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    City city = new City();
                    city.setName(name);
                    city.setSlug(uniqueSlug(name));
                    city.setCountryCode(geo.countryCode() == null ? "RU" : geo.countryCode().toUpperCase());
                    city.setCenterLat(geo.lat());
                    city.setCenterLon(geo.lon());
                    return repository.save(city);
                });
    }

    /** Административные приставки, которые Geoapify подмешивает в название города */
    private static final List<String> ADMIN_PREFIXES = List.of(
            "городской округ ",
            "муниципальное образование ",
            "городское поселение ",
            "сельское поселение ",
            "город ");

    /**
     * Чистит название города от административных приставок:
     * «городской округ Тверь» → «Тверь». Регистронезависимо, в цикле (вложенные приставки)
     */
    private static String cleanCityName(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String prefix : ADMIN_PREFIXES) {
                if (s.regionMatches(true, 0, prefix, 0, prefix.length())) {
                    s = s.substring(prefix.length()).trim();
                    changed = true;
                }
            }
        }
        return s;
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
     * Определяет текущий город по координатам. Если города нет — 404
     */
    @Transactional
    public CityResponse current(double lat, double lon) {
        return resolveCity(lat, lon)
                .map(CityResponse::from)
                .orElseThrow(() -> new NotFoundException("Город пока не поддерживается"));
    }

    /**
     * Город по координатам: reverse geocoding с read-through импортом (как в search).
     * Если reverse не дал результата — ближайший уже известный город из БД.
     * Используется для /current и для привязки мест к городу
     */
    @Transactional
    public Optional<City> resolveCity(double lat, double lon) {
        Optional<GeoCity> geo = geoapifyClient.reverseCity(lat, lon);
        if (geo.isPresent()) {
            return Optional.of(importCity(geo.get()));
        }
        return findNearest(lat, lon);
    }

    /**
     * Ближайший к точке поддерживаемый город из БД (в пределах
     * {@link #MAX_NEAREST_DISTANCE_METERS}). Чистая операция: только читает БД,
     * ничего не импортирует. {@link Optional#empty()}, если такого нет
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
