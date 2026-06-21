package dev.walk.backend.features.place.domain;

/**
 * @author Ilya Samsonov
 * Закрытый перечень категорий мест. SQUARE и STREET зарезервированы под будущие
 * кураторские/OSM-данные — через Geoapify Places сейчас не наполняются
 */
public enum PlaceCategory {
    PARK,
    LANDMARK,
    VIEWPOINT,
    SQUARE,
    MUSEUM,
    CAFE,
    STREET,
    MONUMENT,
    RELIGIOUS
}
