package dev.walk.backend.features.place.application;

import dev.walk.backend.common.config.CacheConfig;
import dev.walk.backend.features.geo.WikipediaClient;
import dev.walk.backend.features.place.domain.Place;
import dev.walk.backend.features.place.domain.PlaceMedia;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * @author Ilya Samsonov
 * Обогащает место фото и описанием из Wikipedia. Результат кэшируется по id места
 * (в т.ч. «пусто» — у места нет статьи), чтобы повторные генерации не ходили в
 * внешний API. Вызывается только для выбранных в прогулку точек (4–6), не на весь пул
 */
@Service
@RequiredArgsConstructor
public class PlaceEnricher {

    private final WikipediaClient wikipedia;

    @Cacheable(cacheNames = CacheConfig.PLACE_MEDIA, key = "#place.id")
    public PlaceMedia enrich(Place place, String cityName) {
        return wikipedia.lookup(place.getName(), cityName, place.getLat(), place.getLon())
                .map(s -> new PlaceMedia(s.thumbnailUrl(), s.extract(), s.pageUrl()))
                .orElse(PlaceMedia.EMPTY);
    }
}
