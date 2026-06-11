package dev.walk.backend.features.place.repository;

import dev.walk.backend.features.place.domain.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * @author Ilya Samsonov
 */
public interface PlaceRepository extends JpaRepository<Place, Long> {

    /**
     * Места в радиусе {@code radius} метров от точки, отсортированные по близости.
     * Поиск через PostGIS: ST_DWithin по geography (метры) + сортировка по ST_Distance
     */
    @Query(value = """
            SELECT id, city_id, name, category, lat, lon, external_id, source, created_at
            FROM places
            WHERE ST_DWithin(geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography, :radius)
            ORDER BY ST_Distance(geom::geography, ST_SetSRID(ST_MakePoint(:lon, :lat), 4326)::geography)
            LIMIT :limit
            """, nativeQuery = true)
    List<Place> findNearby(@Param("lat") double lat,
                           @Param("lon") double lon,
                           @Param("radius") double radius,
                           @Param("limit") int limit);

    boolean existsByExternalId(String externalId);
}
