package dev.walk.backend.features.geo.domain;

import java.util.List;

/**
 * @author Ilya Samsonov
 * Место, найденное Geoapify Places: внешний id, имя, сырые категории и координаты
 */
public record GeoPlace(String externalId, String name, List<String> categories, double lat, double lon) {
}
