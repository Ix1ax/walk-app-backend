package dev.walk.backend.features.place.repository;

import dev.walk.backend.features.place.domain.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * @author Ilya Samsonov
 */
public interface PlaceRepository extends JpaRepository<Place, Long> {

    /**
     * Видимые места в радиусе {@code radius} метров от точки, отсортированные по
     * близости. Поиск через PostGIS: ST_DWithin по geography (метры) + сортировка по
     * ST_Distance. Скрытые (закрытые/пожаловались) не отдаём
     */
    @Query(value = """
            SELECT id, city_id, name, category, lat, lon, external_id, source, hidden, notable, last_seen_at, created_at
            FROM places
            WHERE hidden = false
              AND ST_DWithin(geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radius)
            ORDER BY ST_Distance(geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography)
            LIMIT :limit
            """, nativeQuery = true)
    List<Place> findNearby(@Param("lat") double lat,
                           @Param("lon") double lon,
                           @Param("radius") double radius,
                           @Param("limit") int limit);

    boolean existsByExternalId(String externalId);

    /** Освежает «последний раз виден» у уже существующего места */
    @Modifying
    @Query("UPDATE Place p SET p.lastSeenAt = :ts WHERE p.externalId = :externalId")
    void touchLastSeen(@Param("externalId") String externalId, @Param("ts") Instant ts);
}
