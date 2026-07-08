package dev.walk.backend.features.walk.repository;

import dev.walk.backend.features.walk.domain.WalkEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * @author Ilya Samsonov
 * Прогулки всегда читаются в разрезе владельца ({@code userId}) — чужую по id не
 * достать (для владельца её просто «нет»)
 */
public interface WalkRepository extends JpaRepository<WalkEntity, Long> {

    /**
     * Прогулка владельца с уже подгруженными точками (одним запросом) — чтобы читать
     * точки после закрытия транзакции без ленивой инициализации
     */
    @EntityGraph(attributePaths = "points")
    Optional<WalkEntity> findWithPointsByIdAndUserId(Long id, Long userId);

    /**
     * История прогулок пользователя, сначала свежие. Точки подгружаем графом —
     * сводке нужны количество точек и прогресс
     */
    @EntityGraph(attributePaths = "points")
    List<WalkEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);
}
