package dev.walk.backend.features.place.repository;

import dev.walk.backend.features.place.domain.PlaceCoverage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * @author Ilya Samsonov
 */
public interface PlaceCoverageRepository extends JpaRepository<PlaceCoverage, Long> {

    Optional<PlaceCoverage> findByCellLatAndCellLon(int cellLat, int cellLon);
}
