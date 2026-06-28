package dev.walk.backend.features.place.application;

import dev.walk.backend.features.place.domain.PlaceCategory;

import java.util.List;
import java.util.Optional;

/**
 * @author Ilya Samsonov
 * Сопоставление тегов OpenStreetMap с нашим закрытым {@link PlaceCategory}.
 * Заодно отдаёт список тег-фильтров для запроса Overpass — то, что мы вообще тянем.
 * В отличие от Geoapify, OSM позволяет наполнять SQUARE (place=square) и STREET
 * (highway=pedestrian), поэтому они тоже здесь
 */
public final class OsmCategoryMapper {

    private OsmCategoryMapper() {
    }

    /**
     * Тег-фильтр для Overpass: {@code key=value}; {@code requireName} — брать только
     * именованные объекты (для пешеходных улиц, чтобы не тащить безымянные дорожки)
     */
    public record OsmFilter(String key, String value, boolean requireName) {
    }

    /**
     * Что запрашиваем у Overpass. Порядок не важен — классификация по приоритету в {@link #map}
     */
    public static List<OsmFilter> queryFilters() {
        return List.of(
                new OsmFilter("amenity", "place_of_worship", false),
                new OsmFilter("tourism", "museum", false),
                new OsmFilter("tourism", "gallery", false),
                new OsmFilter("leisure", "park", false),
                new OsmFilter("leisure", "garden", false),
                new OsmFilter("tourism", "viewpoint", false),
                new OsmFilter("historic", "monument", false),
                new OsmFilter("historic", "memorial", false),
                new OsmFilter("tourism", "artwork", false),
                new OsmFilter("place", "square", false),
                new OsmFilter("amenity", "cafe", false),
                new OsmFilter("highway", "pedestrian", true),
                new OsmFilter("tourism", "attraction", false),
                new OsmFilter("historic", "castle", false),
                new OsmFilter("historic", "ruins", false),
                new OsmFilter("historic", "fort", false));
    }

    /**
     * Определяет нашу категорию по тегам OSM (формат {@code "ключ=значение"}).
     * Приоритет: специфичные категории важнее общего tourism.attraction.
     * {@link Optional#empty()}, если ничего не подошло (объект пропускаем)
     */
    public static Optional<PlaceCategory> map(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Optional.empty();
        }
        if (has(tags, "amenity=place_of_worship")) {
            return Optional.of(PlaceCategory.RELIGIOUS);
        }
        if (has(tags, "tourism=museum") || has(tags, "tourism=gallery")) {
            return Optional.of(PlaceCategory.MUSEUM);
        }
        if (has(tags, "leisure=park") || has(tags, "leisure=garden")) {
            return Optional.of(PlaceCategory.PARK);
        }
        if (has(tags, "tourism=viewpoint")) {
            return Optional.of(PlaceCategory.VIEWPOINT);
        }
        if (has(tags, "historic=monument") || has(tags, "historic=memorial") || has(tags, "tourism=artwork")) {
            return Optional.of(PlaceCategory.MONUMENT);
        }
        if (has(tags, "place=square")) {
            return Optional.of(PlaceCategory.SQUARE);
        }
        if (has(tags, "amenity=cafe")) {
            return Optional.of(PlaceCategory.CAFE);
        }
        if (has(tags, "highway=pedestrian")) {
            return Optional.of(PlaceCategory.STREET);
        }
        if (has(tags, "tourism=attraction")
                || has(tags, "historic=castle") || has(tags, "historic=ruins") || has(tags, "historic=fort")) {
            return Optional.of(PlaceCategory.LANDMARK);
        }
        return Optional.empty();
    }

    private static boolean has(List<String> tags, String keyValue) {
        return tags.contains(keyValue);
    }
}
