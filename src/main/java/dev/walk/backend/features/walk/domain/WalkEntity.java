package dev.walk.backend.features.walk.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Ilya Samsonov
 * Сохранённая прогулка (этап 5). В отличие от превью-{@link Walk} персистится и
 * несёт статус/прогресс. Геометрия и плечи маршрута хранятся сериализованным
 * JSON'ом в {@code routeJson} (карта восстанавливается один-в-один), точки —
 * в {@link WalkPointEntity}. Владелец ({@code ownerToken}) — анонимный id
 * устройства до появления auth
 */
@Entity
@Table(name = "walks")
@Getter
@Setter
public class WalkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Владелец прогулки (этап 6). Nullable — старые/анонимные прогулки */
    @Column(name = "user_id")
    private Long userId;

    /** Легаси-владелец до auth (анонимный id устройства). Больше не заполняется */
    @Column(name = "owner_token", length = 128)
    private String ownerToken;

    @Column(name = "city_id")
    private Long cityId;

    @Column(name = "start_lat", nullable = false)
    private double startLat;

    @Column(name = "start_lon", nullable = false)
    private double startLon;

    @Column(name = "return_to_start", nullable = false)
    private boolean returnToStart;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "total_distance_meters", nullable = false)
    private long totalDistanceMeters;

    @Column(name = "walk_minutes", nullable = false)
    private long walkMinutes;

    @Column(name = "dwell_minutes", nullable = false)
    private long dwellMinutes;

    @Column(name = "total_minutes", nullable = false)
    private long totalMinutes;

    @Column(name = "route_estimated", nullable = false)
    private boolean routeEstimated;

    /** Геометрия+плечи маршрута (GeoRoute как JSON) для отрисовки на карте */
    @Column(name = "route_json")
    private String routeJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private WalkStatus status = WalkStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "walk", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    private List<WalkPointEntity> points = new ArrayList<>();

    /** Добавляет точку, связывая обе стороны отношения */
    public void addPoint(WalkPointEntity point) {
        point.setWalk(this);
        points.add(point);
    }

    /** Сколько точек уже пройдено */
    public int visitedCount() {
        return (int) points.stream().filter(WalkPointEntity::isVisited).count();
    }
}
