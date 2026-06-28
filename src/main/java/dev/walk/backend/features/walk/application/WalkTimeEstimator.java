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
        // На прогулке точки в основном осматривают на ходу — пребывание короткое,
        // чтобы бюджет времени вёл именно ХОДЬБА, а не «стоянки» (иначе «2 часа»
        // превращаются в 30 минут реального хода)
        return switch (category) {
            case MUSEUM -> 8;    // беглый осмотр снаружи/у входа, не часовой визит
            case CAFE -> 7;
            case PARK -> 6;
            case RELIGIOUS -> 5;
            case LANDMARK, SQUARE, VIEWPOINT -> 4;
            case STREET -> 3;
            case MONUMENT -> 3;
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
