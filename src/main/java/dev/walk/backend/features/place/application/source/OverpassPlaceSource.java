package dev.walk.backend.features.place.application.source;

import dev.walk.backend.features.geo.OverpassClient;
import dev.walk.backend.features.geo.domain.OsmPlace;
import dev.walk.backend.features.place.application.OsmCategoryMapper;
import dev.walk.backend.features.place.domain.PlaceCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * @author Ilya Samsonov
 * Источник мест на OpenStreetMap (Overpass). Бесплатно, без ключа. Один запрос на
 * зону, классификация по тегам OSM
 */
@Component
@RequiredArgsConstructor
public class OverpassPlaceSource implements PlaceSource {

    /**
     * Категории, значимые сами по себе (точки притяжения, ради которых и идут гулять)
     */
    private static final Set<PlaceCategory> ANCHOR_CATEGORIES = Set.of(
            PlaceCategory.MUSEUM, PlaceCategory.RELIGIOUS,
            PlaceCategory.LANDMARK, PlaceCategory.VIEWPOINT);

    private final OverpassClient client;

    @Override
    public String id() {
        return "osm";
    }

    @Override
    public List<SourcePlace> fetchArea(double lat, double lon, int radiusMeters) {
        List<SourcePlace> result = new ArrayList<>();
        for (OsmPlace osm : client.fetchArea(lat, lon, radiusMeters)) {
            Optional<PlaceCategory> category = OsmCategoryMapper.map(osm.tags());
            if (category.isEmpty()) {
                continue; // теги не из нашего набора
            }
            result.add(new SourcePlace(
                    osm.externalId(), osm.name(), category.get(), osm.lat(), osm.lon(),
                    isNotable(osm.tags(), category.get())));
        }
        return result;
    }

    /**
     * Значимость: курируемый сигнал OSM (wikidata/wikipedia/heritage) ИЛИ якорная
     * категория. Так настоящие достопримечательности и музеи/храмы получают приоритет
     * над случайными мемориальными табличками
     */
    private static boolean isNotable(List<String> tags, PlaceCategory category) {
        if (ANCHOR_CATEGORIES.contains(category)) {
            return true;
        }
        for (String tag : tags) {
            if (tag.startsWith("wikidata=") || tag.startsWith("wikipedia=") || tag.startsWith("heritage=")) {
                return true;
            }
        }
        return false;
    }
}
