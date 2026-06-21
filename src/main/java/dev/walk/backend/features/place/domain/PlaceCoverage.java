package dev.walk.backend.features.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * @author Ilya Samsonov
 * Отметка, что зона (квантованная ячейка ~1.1 км) уже загружена из Geoapify
 */
@Entity
@Table(name = "place_coverage")
@Getter
@Setter
public class PlaceCoverage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cell_lat", nullable = false)
    private int cellLat;

    @Column(name = "cell_lon", nullable = false)
    private int cellLon;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
