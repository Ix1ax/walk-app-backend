package dev.walk.backend.features.place.application.source;

import dev.walk.backend.features.place.domain.PlaceCategory;

/**
 * @author Ilya Samsonov
 * Место от источника, уже приведённое к нашей категории. Маппинг сырых данных
 * (Geoapify/OSM) в {@link PlaceCategory} живёт внутри каждого {@link PlaceSource},
 * поэтому импортёр от конкретного источника не зависит
 */
public record SourcePlace(
        String externalId,
        String name,
        PlaceCategory category,
        double lat,
        double lon,
        boolean notable
) {
}
