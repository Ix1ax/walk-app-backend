package dev.walk.backend.features.walk.application;

import dev.walk.backend.features.place.domain.PlaceCategory;
import org.springframework.stereotype.Component;

/**
 * @author Ilya Samsonov
 * Оценка времени прогулки: суммарное время = Σ(время пребывания в точках) +
 * Σ(пешие переходы). Реального маршрута пока нет (роутинг — этап 4), поэтому
 * переход считается по прямой (гаверсинус) с поправочным коэффициентом на
 * извилистость улиц {@link #DETOUR_FACTOR}
 */
@Component
public class WalkTimeEstimator {

    /**
     * Средняя скорость пешехода, м/мин (≈4.8 км/ч — прогулочный, не спортивный шаг)
     */
    private static final double WALK_SPEED_METERS_PER_MIN = 80.0;
    /**
     * Поправка «по прямой → по улицам»: реальный путь длиннее воздушной линии
     */
    private static final double DETOUR_FACTOR = 1.3;

    /**
     * Сколько минут в среднем человек проводит в точке данной категории
     */
    public int dwellMinutes(PlaceCategory category) {
        return switch (category) {
            case CAFE -> 15;     // короткая остановка по пути, а не полноценный обед
            case MUSEUM -> 12;   // на прогулке — осмотр снаружи/беглый заход, не часовой визит
            case PARK -> 12;
            case RELIGIOUS -> 8;
            case LANDMARK, SQUARE, STREET -> 6;
            case VIEWPOINT -> 6;
            case MONUMENT -> 4;
        };
    }

    /**
     * Реальная длина пешего перехода (м) по прямому расстоянию между точками —
     * с поправкой на извилистость
     */
    public long legMeters(double straightMeters) {
        return Math.round(straightMeters * DETOUR_FACTOR);
    }

    /**
     * Время пешего перехода (мин) по уже посчитанной длине перехода
     */
    public double walkMinutes(double legMeters) {
        return legMeters / WALK_SPEED_METERS_PER_MIN;
    }
}
