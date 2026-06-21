package dev.walk.backend.features.place.domain;

/**
 * @author Ilya Samsonov
 * Медиа-обогащение места для показа в превью: превью-фото, краткое описание и
 * ссылка на источник. Любое поле может быть null. {@link #EMPTY} — место без
 * данных (нет статьи); кэшируется наравне с заполненным, чтобы не дёргать API
 */
public record PlaceMedia(String imageUrl, String description, String sourceUrl) {

    public static final PlaceMedia EMPTY = new PlaceMedia(null, null, null);
}
