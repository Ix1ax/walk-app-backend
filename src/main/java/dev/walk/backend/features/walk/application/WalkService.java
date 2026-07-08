package dev.walk.backend.features.walk.application;

import dev.walk.backend.common.exception.ConflictException;
import dev.walk.backend.common.exception.NotFoundException;
import dev.walk.backend.features.geo.domain.GeoDistance;
import dev.walk.backend.features.place.application.PlaceService;
import dev.walk.backend.features.place.domain.Place;
import dev.walk.backend.features.place.repository.PlaceRepository;
import dev.walk.backend.features.walk.api.dto.GenerateWalkRequest;
import dev.walk.backend.features.walk.api.dto.RerouteRequest;
import dev.walk.backend.features.walk.api.dto.SavedWalkResponse;
import dev.walk.backend.features.walk.api.dto.WalkSummaryResponse;
import dev.walk.backend.features.walk.application.WalkStore.PointSnap;
import dev.walk.backend.features.walk.application.WalkStore.ReplaceContext;
import dev.walk.backend.features.walk.application.WalkStore.RerouteContext;
import dev.walk.backend.features.walk.domain.Walk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author Ilya Samsonov
 * Сохранение прогулок и операции над ними: создание (генерация + персист), чтение/
 * история, отметка пройденных точек (прогресс), <b>замена отдельной точки</b>
 * (заменить, например, 3-ю точку, где уже были, на другую рядом) и
 * <b>перестроение маршрута от нового местоположения</b> по тем же ещё не
 * посещённым точкам (пользователь свернул с маршрута).
 * <p>
 * Внешние вызовы (роутинг Geoapify, обогащение) и подтягивание пула мест делаются
 * ЗДЕСЬ — вне транзакции; собственно запись в БД — в транзакционном
 * {@link WalkStore}. Так транзакция не держится на время внешнего API и её не
 * отравляют гонки импорта мест (см. {@code PlaceService})
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalkService {

    /**
     * Минимальное расстояние (м) от замены до других точек прогулки — чтобы новая
     * точка не оказалась дублем/стопкой рядом с уже имеющейся
     */
    private static final double MIN_SPACING_METERS = 50.0;

    private final WalkStore store;
    private final WalkGenerator generator;
    private final WalkAssembler assembler;
    private final RouteOptimizer optimizer;
    private final NearbyPlacesSelector selector;
    private final PlaceService placeService;
    private final PlaceRepository placeRepository;

    /**
     * Генерирует прогулку под запрос и сохраняет её на пользователя {@code userId}
     */
    public SavedWalkResponse create(GenerateWalkRequest request, long userId) {
        Walk walk = generator.generate(
                request.lat(), request.lon(), request.durationOrDefault(), request.returnToStartOrDefault());
        SavedWalkResponse saved = store.saveNew(walk, request.durationOrDefault(), userId);
        log.info("Прогулка сохранена id={}, точек={}, пользователь={}",
                saved.id(), saved.pointsCount(), userId);
        return saved;
    }

    public SavedWalkResponse get(long id, long userId) {
        return store.get(id, userId);
    }

    public List<WalkSummaryResponse> history(long userId) {
        return store.list(userId);
    }

    public void delete(long id, long userId) {
        store.delete(id, userId);
    }

    /**
     * Отмечает точку прогулки пройденной/непройденной. Когда пройдены все — прогулка
     * автоматически завершается (COMPLETED)
     */
    public SavedWalkResponse setVisited(long id, int seq, boolean visited, long userId) {
        return store.markVisited(id, seq, visited, userId);
    }

    /**
     * Заменяет одну точку прогулки на другую подходящую рядом (например, «в этой уже
     * был»). Набор остальных точек и их порядок сохраняются, посещённость остальных —
     * тоже; маршрут и время пересчитываются. Нельзя заменить уже посещённую точку
     */
    public SavedWalkResponse replacePoint(long id, int seq, long userId) {
        ReplaceContext ctx = store.loadForReplace(id, seq, userId);

        Place replacement = pickReplacement(ctx);
        List<Place> ordered = buildOrderWithReplacement(ctx, replacement);
        Walk assembled = assembler.assemble(ctx.startLat(), ctx.startLon(), ordered, ctx.returnToStart());

        SavedWalkResponse saved = store.applyReplacement(id, seq, assembled, userId);
        log.info("Прогулка id={}: точка №{} заменена на '{}' (id={})",
                id, seq, replacement.getName(), replacement.getId());
        return saved;
    }

    /**
     * Перестраивает маршрут прогулки от нового местоположения по тем же ещё не
     * посещённым точкам: пользователь свернул с маршрута — строим эффективный путь
     * от текущей точки. Старт обновляется, посещённые точки остаются пройденными,
     * непосещённые переупорядочиваются под новый старт
     */
    public SavedWalkResponse reroute(long id, RerouteRequest request, long userId) {
        RerouteContext ctx = store.loadForReroute(id, userId);

        List<Place> places = placeRepository.findAllById(ctx.unvisitedPlaceIds());
        if (places.isEmpty()) {
            throw new ConflictException("Места прогулки недоступны — перестроить маршрут нельзя");
        }
        List<Place> ordered = optimizer.order(request.lat(), request.lon(), places, ctx.returnToStart());
        Walk assembled = assembler.assemble(request.lat(), request.lon(), ordered, ctx.returnToStart());

        SavedWalkResponse saved = store.applyReroute(id, request.lat(), request.lon(), assembled, userId);
        log.info("Прогулка id={}: маршрут перестроен от ({},{}), точек={}",
                id, request.lat(), request.lon(), saved.pointsCount());
        return saved;
    }

    /* ============================== Внутреннее ============================== */

    /**
     * Подбирает замену для точки: ближайшее подходящее место у координат заменяемой
     * точки, которого ещё нет в прогулке и которое не слипается с другими точками
     */
    private Place pickReplacement(ReplaceContext ctx) {
        List<Place> pool = placeService.poolNearby(ctx.startLat(), ctx.startLon(), WalkGenerator.POOL_RADIUS_METERS);
        List<Place> ranked = selector.rank(ctx.replacedLat(), ctx.replacedLon(), pool);

        Set<Long> exclude = new HashSet<>(ctx.currentPlaceIds());
        List<PointSnap> kept = ctx.points().stream()
                .filter(s -> s.seq() != ctx.replacedSeq())
                .toList();

        for (Place p : ranked) {
            if (exclude.contains(p.getId())) {
                continue;
            }
            if (tooCloseToKept(p, kept)) {
                continue;
            }
            return p;
        }
        throw new NotFoundException("Не нашлось подходящей замены рядом");
    }

    private static boolean tooCloseToKept(Place p, List<PointSnap> kept) {
        for (PointSnap s : kept) {
            if (GeoDistance.haversineMeters(p.getLat(), p.getLon(), s.lat(), s.lon()) < MIN_SPACING_METERS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Восстанавливает упорядоченный список мест прогулки, подставляя на место
     * заменяемой точки {@code replacement}. Порядок остальных точек сохраняется
     */
    private List<Place> buildOrderWithReplacement(ReplaceContext ctx, Place replacement) {
        List<Long> keptIds = ctx.points().stream()
                .filter(s -> s.seq() != ctx.replacedSeq() && s.placeId() != null)
                .map(PointSnap::placeId)
                .toList();
        Map<Long, Place> byId = placeRepository.findAllById(keptIds).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));

        List<Place> ordered = new ArrayList<>(ctx.points().size());
        for (PointSnap s : ctx.points()) {
            if (s.seq() == ctx.replacedSeq()) {
                ordered.add(replacement);
                continue;
            }
            Place p = s.placeId() != null ? byId.get(s.placeId()) : null;
            if (p == null) {
                throw new ConflictException(
                        "Место точки №" + s.seq() + " больше недоступно — перестройте маршрут");
            }
            ordered.add(p);
        }
        return ordered;
    }
}
