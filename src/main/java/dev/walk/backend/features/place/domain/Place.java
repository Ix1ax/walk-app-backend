package dev.walk.backend.features.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * @author Ilya Samsonov
 * Интересное место (точка притяжения) для прогулки.
 * Геометрия (geom) считается в БД из lat/lon и здесь намеренно не маппится
 */
@Entity
@Table(name = "places")
@Getter
@Setter
public class Place {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "city_id")
    private Long cityId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PlaceCategory category;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false)
    private String source;

    /** Скрыто из выдачи (закрыто / пожаловались) */
    @Column(nullable = false)
    private boolean hidden;

    /** Когда место последний раз приходило из Geoapify (для пруна «призраков») */
    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
