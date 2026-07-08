package dev.walk.backend.features.walk.domain;

import dev.walk.backend.features.place.domain.PlaceCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * @author Ilya Samsonov
 * Точка сохранённой прогулки. Ключевые поля места сняты снимком (name/category/
 * lat/lon/медиа) — прогулка самодостаточна и не ломается, если место позже
 * скрыли/удалили. {@code placeId} нужен для замены точки (исключить уже вошедшие
 * места) и перестроения маршрута. {@code visited} — прогресс прогулки
 */
@Entity
@Table(name = "walk_points")
@Getter
@Setter
public class WalkPointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "walk_id", nullable = false)
    private WalkEntity walk;

    @Column(name = "place_id")
    private Long placeId;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PlaceCategory category;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    @Column(name = "leg_from_prev_meters", nullable = false)
    private long legFromPrevMeters;

    @Column(name = "leg_from_prev_seconds", nullable = false)
    private long legFromPrevSeconds;

    @Column(name = "dwell_minutes", nullable = false)
    private int dwellMinutes;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "description")
    private String description;

    @Column(name = "info_url")
    private String infoUrl;

    @Column(nullable = false)
    private boolean visited;

    @Column(name = "visited_at")
    private Instant visitedAt;
}
