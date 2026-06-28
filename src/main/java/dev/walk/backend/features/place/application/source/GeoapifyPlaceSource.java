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

/**
 * @author Ilya Samsonov
 * Источник мест на Geoapify Places (платный/квотируемый). Тянет по каждой категории
 * отдельно с квотой — чтобы пул был сбалансирован по типам. Оставлен как альтернатива
 * OSM; активируется конфигом {@code walk.places.source=geoapify}
 */
@Component
@RequiredArgsConstructor
public class GeoapifyPlaceSource implements PlaceSource {

    /**
     * Квота мест на каждую категорию Geoapify за проход
     */
    private static final int PER_CATEGORY_LIMIT = 50;

    private final GeoapifyClient client;

    @Override
    public String id() {
        return "geoapify";
    }

    @Override
    public List<SourcePlace> fetchArea(double lat, double lon, int radiusMeters) {
        List<SourcePlace> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String category : PlaceCategoryMapper.geoapifyRequestCategories()) {
            List<GeoPlace> found = client.searchPlaces(lat, lon, radiusMeters, List.of(category), PER_CATEGORY_LIMIT);
            for (GeoPlace geo : found) {
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
