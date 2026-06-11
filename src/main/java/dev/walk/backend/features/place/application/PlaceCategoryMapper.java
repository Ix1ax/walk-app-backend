package dev.walk.backend.features.place.application;

import dev.walk.backend.features.place.domain.PlaceCategory;

import java.util.List;
import java.util.Optional;

/**
 * @author Ilya Samsonov
 * Сопоставление категорий Geoapify Places с нашим закрытым {@link PlaceCategory}.
 * У одного места обычно несколько категорий (напр. собор — religion + tourism),
 * поэтому проверяем правила по приоритету: от специфичных к общим
 */
public final class PlaceCategoryMapper {

    private PlaceCategoryMapper() {
    }

    /**
     * Категории, которые запрашиваем у Geoapify Places (наполняемые из read-through).
     * SQUARE и STREET сюда не входят — для них у Geoapify нет чистой категории
     */
    public static List<String> geoapifyRequestCategories() {
        return List.of(
                "tourism.sights",
                "tourism.attraction",
                "leisure.park",
                "catering.cafe",
                "entertainment.museum",
                "religion.place_of_worship");
    }

    /**
     * Определяет нашу категорию по списку категорий Geoapify.
     * {@link Optional#empty()}, если ничего не подошло (место пропускаем)
     */
    public static Optional<PlaceCategory> map(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return Optional.empty();
        }
        // Приоритет: специфичные категории важнее общего tourism.*
        if (any(categories, "place_of_worship")) {
            return Optional.of(PlaceCategory.RELIGIOUS);
        }
        if (any(categories, "entertainment.museum")) {
            return Optional.of(PlaceCategory.MUSEUM);
        }
        if (any(categories, "leisure.park")) {
            return Optional.of(PlaceCategory.PARK);
        }
        if (any(categories, "catering.cafe")) {
            return Optional.of(PlaceCategory.CAFE);
        }
        if (any(categories, "memorial") || any(categories, "monument") || any(categories, "artwork.statue")) {
            return Optional.of(PlaceCategory.MONUMENT);
        }
        if (any(categories, "viewpoint")) {
            return Optional.of(PlaceCategory.VIEWPOINT);
        }
        if (any(categories, "tourism.attraction") || any(categories, "tourism.sights")) {
            return Optional.of(PlaceCategory.LANDMARK);
        }
        return Optional.empty();
    }

    private static boolean any(List<String> categories, String needle) {
        for (String c : categories) {
            if (c != null && c.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
