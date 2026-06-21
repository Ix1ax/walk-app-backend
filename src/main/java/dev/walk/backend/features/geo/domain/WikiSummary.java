package dev.walk.backend.features.geo.domain;

/**
 * @author Ilya Samsonov
 * Краткая выжимка статьи Wikipedia о месте: заголовок, описание (extract),
 * ссылка на превью-фото и на саму статью. Любое поле, кроме title, может быть null
 */
public record WikiSummary(
        String title,
        String extract,
        String thumbnailUrl,
        String pageUrl
) {
}
