package dev.walk.backend.features.geo.domain;

/**
 * @author Ilya Samsonov
 * Город, найденный геокодером (Geoapify): имя, код страны и координаты центра
 */
public record GeoCity(String name, String countryCode, double lat, double lon) {
}
