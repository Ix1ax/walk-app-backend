package dev.walk.backend.features.place.application.source;

import java.util.List;

/**
 * @author Ilya Samsonov
 * Источник интересных мест по зоне. Реализации (Geoapify, OSM/Overpass) сами ходят
 * во внешний сервис и классифицируют места в наши категории. Активный источник
 * выбирается конфигом {@code walk.places.source}
 */
public interface PlaceSource {

    /**
     * Идентификатор источника (он же пишется в {@code places.source}): напр. «osm», «geoapify»
     */
    String id();

    /**
     * Места в радиусе {@code radiusMeters} от точки. Пустой список при ошибке/отсутствии данных
     */
    List<SourcePlace> fetchArea(double lat, double lon, int radiusMeters);
}
