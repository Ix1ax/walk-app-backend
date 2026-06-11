package dev.walk.backend.features.city.repository;

import dev.walk.backend.features.city.domain.City;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * @author Ilya Samsonov
 */
public interface CityRepository extends JpaRepository<City, Long> {

    List<City> findAllByOrderByNameAsc();

    // Поиск по названию (кириллица) ИЛИ по slug (латиница) — чтобы работали
    // и «Моск», и «Moscow».
    List<City> findByNameContainingIgnoreCaseOrSlugContainingIgnoreCaseOrderByNameAsc(String name, String slug);

    Optional<City> findBySlug(String slug);

    Optional<City> findByNameIgnoreCase(String name);
}
