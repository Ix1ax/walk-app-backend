package dev.walk.backend.features.walk.api;

import dev.walk.backend.features.auth.application.UserPrincipal;
import dev.walk.backend.features.walk.api.dto.GenerateWalkRequest;
import dev.walk.backend.features.walk.api.dto.RerouteRequest;
import dev.walk.backend.features.walk.api.dto.SavedWalkResponse;
import dev.walk.backend.features.walk.api.dto.WalkResponse;
import dev.walk.backend.features.walk.api.dto.WalkSummaryResponse;
import dev.walk.backend.features.walk.application.WalkGenerator;
import dev.walk.backend.features.walk.application.WalkService;
import dev.walk.backend.features.walk.domain.Walk;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author Ilya Samsonov
 * API прогулок: превью-генерация без сохранения, а также полный цикл сохранённой
 * прогулки — создать, открыть, история, отметка пройденных точек, замена одной
 * точки и перестроение маршрута от нового местоположения.
 * <p>
 * Сохранённые прогулки принадлежат авторизованному пользователю: нужен заголовок
 * {@code Authorization: Bearer <token>} (получить в {@code /api/v1/auth}). Превью
 * ({@code /generate}) — публичное
 */
@Validated
@RestController
@RequestMapping("/api/v1/walks")
@RequiredArgsConstructor
public class WalkController {

    private final WalkGenerator generator;
    private final WalkService service;

    /**
     * Превью пешей прогулки от точки старта под желаемую длительность. Ничего не
     * сохраняет — для сохранения используйте {@code POST /api/v1/walks}
     */
    @PostMapping("/generate")
    public WalkResponse generate(@Valid @RequestBody GenerateWalkRequest request) {
        Walk walk = generator.generate(
                request.lat(), request.lon(), request.durationOrDefault(), request.returnToStartOrDefault());
        return WalkResponse.from(walk);
    }

    /**
     * Генерирует прогулку и сохраняет её на текущего пользователя. Возвращает
     * сохранённую прогулку с id, по которому ей потом управляют
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedWalkResponse create(@Valid @RequestBody GenerateWalkRequest request,
                                    @AuthenticationPrincipal UserPrincipal user) {
        return service.create(request, user.id());
    }

    /**
     * Открыть свою сохранённую прогулку (метрики, геометрия для карты, точки, прогресс)
     */
    @GetMapping("/{id}")
    public SavedWalkResponse get(@PathVariable long id, @AuthenticationPrincipal UserPrincipal user) {
        return service.get(id, user.id());
    }

    /**
     * История прогулок текущего пользователя
     */
    @GetMapping
    public List<WalkSummaryResponse> history(@AuthenticationPrincipal UserPrincipal user) {
        return service.history(user.id());
    }

    /**
     * Удалить свою сохранённую прогулку
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, @AuthenticationPrincipal UserPrincipal user) {
        service.delete(id, user.id());
    }

    /**
     * Отметить точку прогулки пройденной (прогресс). Когда пройдены все — прогулка
     * завершается автоматически
     */
    @PostMapping("/{id}/points/{seq}/visit")
    public SavedWalkResponse markVisited(@PathVariable long id, @PathVariable @Min(1) int seq,
                                         @AuthenticationPrincipal UserPrincipal user) {
        return service.setVisited(id, seq, true, user.id());
    }

    /**
     * Снять отметку «пройдено» с точки
     */
    @DeleteMapping("/{id}/points/{seq}/visit")
    public SavedWalkResponse unmarkVisited(@PathVariable long id, @PathVariable @Min(1) int seq,
                                           @AuthenticationPrincipal UserPrincipal user) {
        return service.setVisited(id, seq, false, user.id());
    }

    /**
     * Заменить одну точку прогулки на другую подходящую рядом (например, «в этой уже
     * был»). Порядок и посещённость остальных точек сохраняются, маршрут и время
     * пересчитываются. Посещённую точку заменить нельзя (409)
     */
    @PutMapping("/{id}/points/{seq}/replace")
    public SavedWalkResponse replacePoint(@PathVariable long id, @PathVariable @Min(1) int seq,
                                          @AuthenticationPrincipal UserPrincipal user) {
        return service.replacePoint(id, seq, user.id());
    }

    /**
     * Перестроить маршрут от нового местоположения по тем же ещё не посещённым
     * точкам (пользователь свернул с маршрута). Старт обновляется, порядок
     * непосещённых точек переоптимизируется под новую точку
     */
    @PostMapping("/{id}/reroute")
    public SavedWalkResponse reroute(@PathVariable long id, @Valid @RequestBody RerouteRequest request,
                                     @AuthenticationPrincipal UserPrincipal user) {
        return service.reroute(id, request, user.id());
    }
}
