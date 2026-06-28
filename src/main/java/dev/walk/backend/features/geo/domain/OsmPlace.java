package dev.walk.backend.features.geo.domain;

import java.util.List;

/**
 * @author Ilya Samsonov
 * Объект из OpenStreetMap (Overpass): тип+id ({@code node/123}), имя, координаты и
 * сырые теги в виде {@code "ключ=значение"} (по ним классифицируется категория)
 */
public record OsmPlace(String externalId, String name, double lat, double lon, List<String> tags) {
}
