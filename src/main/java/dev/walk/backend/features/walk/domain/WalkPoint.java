package dev.walk.backend.features.walk.domain;

import dev.walk.backend.features.place.domain.Place;
import dev.walk.backend.features.place.domain.PlaceMedia;

/**
 * @author Ilya Samsonov
 * Точка прогулки: место + параметры перехода к нему от предыдущей точки (или от
 * старта для первой), время пребывания и медиа (фото/описание). Не персистится —
 * это часть превью, сохранение прогулок появится на этапе 5
 *
 * @param order            порядковый номер в маршруте, с 1
 * @param legFromPrevMeters длина пешего перехода от предыдущей точки/старта (с поправкой на улицы)
 * @param legFromPrevMinutes время этого перехода, мин
 * @param dwellMinutes     ориентировочное время пребывания в точке, мин
 * @param media            фото и описание места (поля могут быть null)
 */
public record WalkPoint(
        Place place,
        int order,
        long legFromPrevMeters,
        double legFromPrevMinutes,
        int dwellMinutes,
        PlaceMedia media
) {
}
