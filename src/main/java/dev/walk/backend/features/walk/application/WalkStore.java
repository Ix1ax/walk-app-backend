package dev.walk.backend.features.walk.application;

import dev.walk.backend.common.exception.ConflictException;
import dev.walk.backend.common.exception.NotFoundException;
import dev.walk.backend.features.place.domain.PlaceMedia;
import dev.walk.backend.features.walk.api.dto.SavedWalkResponse;
import dev.walk.backend.features.walk.api.dto.WalkSummaryResponse;
import dev.walk.backend.features.walk.domain.Walk;
import dev.walk.backend.features.walk.domain.WalkEntity;
import dev.walk.backend.features.walk.domain.WalkPoint;
import dev.walk.backend.features.walk.domain.WalkPointEntity;
import dev.walk.backend.features.walk.domain.WalkStatus;
import dev.walk.backend.features.walk.repository.WalkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Ilya Samsonov
 * Транзакционный слой хранения прогулок: только работа с БД, без внешних вызовов
 * (роутинг/обогащение делаются в {@link WalkService} ДО входа в транзакцию, чтобы
 * не держать транзакцию открытой на время внешнего API и не отравлять её гонками
 * импорта мест). Отдаёт готовые DTO — маппинг идёт внутри транзакции, поэтому
 * ленивых инициализаций наружу не утекает
 */
@Service
@RequiredArgsConstructor
public class WalkStore {

    private final WalkRepository repository;
    private final RouteJsonCodec codec;

    /* ================================ Чтение ================================ */

    @Transactional(readOnly = true)
    public SavedWalkResponse get(long id, long userId) {
        return toResponse(require(id, userId));
    }

    @Transactional(readOnly = true)
    public List<WalkSummaryResponse> list(long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(WalkSummaryResponse::from)
                .toList();
    }

    /**
     * Контекст для замены точки: всё, что нужно снаружи, чтобы подобрать замену и
     * пересобрать маршрут, — без удержания транзакции. Валидирует seq
     */
    @Transactional(readOnly = true)
    public ReplaceContext loadForReplace(long id, int seq, long userId) {
        WalkEntity walk = require(id, userId);
        WalkPointEntity target = walk.getPoints().stream()
                .filter(p -> p.getSeq() == seq)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("В прогулке нет точки №" + seq));
        if (target.isVisited()) {
            throw new ConflictException("Нельзя заменить уже посещённую точку №" + seq);
        }
        List<PointSnap> snaps = walk.getPoints().stream()
                .map(p -> new PointSnap(p.getSeq(), p.getPlaceId(), p.getLat(), p.getLon(), p.isVisited()))
                .toList();
        return new ReplaceContext(
                walk.getStartLat(), walk.getStartLon(), walk.isReturnToStart(),
                seq, target.getLat(), target.getLon(), snaps);
    }

    /**
     * Контекст для перестроения маршрута: непосещённые места (в текущем порядке —
     * снаружи он переоптимизируется от нового старта). Требует хотя бы одну такую точку
     */
    @Transactional(readOnly = true)
    public RerouteContext loadForReroute(long id, long userId) {
        WalkEntity walk = require(id, userId);
        List<Long> unvisited = walk.getPoints().stream()
                .filter(p -> !p.isVisited())
                .map(WalkPointEntity::getPlaceId)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (unvisited.isEmpty()) {
            throw new ConflictException("Все точки уже посещены — перестраивать нечего");
        }
        return new RerouteContext(walk.isReturnToStart(), unvisited);
    }

    /* ================================ Запись ================================ */

    @Transactional
    public SavedWalkResponse saveNew(Walk walk, int durationMinutes, long userId) {
        WalkEntity e = new WalkEntity();
        Instant now = Instant.now();
        e.setUserId(userId);
        e.setStartLat(walk.start().lat());
        e.setStartLon(walk.start().lon());
        e.setDurationMinutes(durationMinutes);
        e.setStatus(WalkStatus.ACTIVE);
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        applyMetrics(e, walk);
        for (WalkPoint wp : walk.points()) {
            WalkPointEntity pe = new WalkPointEntity();
            fill(pe, wp);
            e.addPoint(pe);
        }
        repository.save(e);
        return toResponse(e);
    }

    /**
     * Применяет замену одной точки: набор/порядок точек тот же, поэтому обновляем
     * строки на месте (сохраняя посещённость и её время у остальных). Заменённая
     * точка становится непосещённой. Метрики и геометрия пересчитаны снаружи
     */
    @Transactional
    public SavedWalkResponse applyReplacement(long id, int replacedSeq, Walk assembled, long userId) {
        WalkEntity e = require(id, userId);
        Map<Integer, WalkPointEntity> bySeq = new HashMap<>();
        for (WalkPointEntity pe : e.getPoints()) {
            bySeq.put(pe.getSeq(), pe);
        }
        for (WalkPoint wp : assembled.points()) {
            WalkPointEntity pe = bySeq.get(wp.order());
            if (pe == null) {
                // Страховка: набор точек изменился (не должно быть при замене 1-в-1)
                pe = new WalkPointEntity();
                e.addPoint(pe);
            }
            fill(pe, wp);
            if (wp.order() == replacedSeq) {
                pe.setVisited(false);
                pe.setVisitedAt(null);
            }
        }
        applyMetrics(e, assembled);
        e.setUpdatedAt(Instant.now());
        recomputeStatus(e);
        repository.save(e);
        return toResponse(e);
    }

    /**
     * Применяет перестроение от нового старта: старые точки заменяются на новый
     * (непосещённый) набор, стартовая точка обновляется, прогулка снова ACTIVE
     */
    @Transactional
    public SavedWalkResponse applyReroute(long id, double newLat, double newLon, Walk assembled, long userId) {
        WalkEntity e = require(id, userId);
        // Полностью переписываем точки: чистим (orphanRemoval удалит старые строки),
        // фиксируем удаление отдельным flush'ем — иначе новые строки с теми же seq
        // столкнутся с UNIQUE(walk_id, seq) до удаления старых
        e.getPoints().clear();
        repository.saveAndFlush(e);
        for (WalkPoint wp : assembled.points()) {
            WalkPointEntity pe = new WalkPointEntity();
            fill(pe, wp);
            e.addPoint(pe);
        }
        e.setStartLat(newLat);
        e.setStartLon(newLon);
        e.setStatus(WalkStatus.ACTIVE);
        applyMetrics(e, assembled);
        e.setUpdatedAt(Instant.now());
        repository.save(e);
        return toResponse(e);
    }

    /**
     * Отмечает точку пройденной/непройденной (прогресс прогулки). Когда все точки
     * пройдены — прогулка становится COMPLETED, иначе ACTIVE
     */
    @Transactional
    public SavedWalkResponse markVisited(long id, int seq, boolean visited, long userId) {
        WalkEntity e = require(id, userId);
        WalkPointEntity target = e.getPoints().stream()
                .filter(p -> p.getSeq() == seq)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("В прогулке нет точки №" + seq));
        target.setVisited(visited);
        target.setVisitedAt(visited ? Instant.now() : null);
        recomputeStatus(e);
        e.setUpdatedAt(Instant.now());
        repository.save(e);
        return toResponse(e);
    }

    @Transactional
    public void delete(long id, long userId) {
        if (!repository.existsByIdAndUserId(id, userId)) {
            throw new NotFoundException("Прогулка не найдена");
        }
        repository.deleteById(id);
    }

    /* ============================== Внутреннее ============================== */

    private WalkEntity require(long id, long userId) {
        return repository.findWithPointsByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Прогулка не найдена"));
    }

    private void applyMetrics(WalkEntity e, Walk walk) {
        e.setCityId(walk.cityId());
        e.setReturnToStart(walk.returnToStart());
        e.setTotalDistanceMeters(walk.totalDistanceMeters());
        e.setWalkMinutes(walk.walkMinutes());
        e.setDwellMinutes(walk.dwellMinutes());
        e.setTotalMinutes(walk.totalMinutes());
        e.setRouteEstimated(walk.route().estimated());
        e.setRouteJson(codec.encode(walk.route()));
    }

    private void fill(WalkPointEntity pe, WalkPoint wp) {
        PlaceMedia media = wp.media() != null ? wp.media() : PlaceMedia.EMPTY;
        pe.setPlaceId(wp.place().getId());
        pe.setSeq(wp.order());
        pe.setName(wp.place().getName());
        pe.setCategory(wp.place().getCategory());
        pe.setLat(wp.place().getLat());
        pe.setLon(wp.place().getLon());
        pe.setLegFromPrevMeters(wp.legFromPrevMeters());
        pe.setLegFromPrevSeconds(Math.round(wp.legFromPrevMinutes() * 60));
        pe.setDwellMinutes(wp.dwellMinutes());
        pe.setImageUrl(media.imageUrl());
        pe.setDescription(media.description());
        pe.setInfoUrl(media.sourceUrl());
    }

    private static void recomputeStatus(WalkEntity e) {
        boolean allVisited = !e.getPoints().isEmpty()
                && e.getPoints().stream().allMatch(WalkPointEntity::isVisited);
        e.setStatus(allVisited ? WalkStatus.COMPLETED : WalkStatus.ACTIVE);
    }

    private SavedWalkResponse toResponse(WalkEntity e) {
        return SavedWalkResponse.from(e, codec.decode(e.getRouteJson()));
    }

    /* =============================== Контексты ============================== */

    /**
     * Снимок точки для операций замены/перестроения (только нужные поля)
     */
    public record PointSnap(int seq, Long placeId, double lat, double lon, boolean visited) {
    }

    public record ReplaceContext(
            double startLat, double startLon, boolean returnToStart,
            int replacedSeq, double replacedLat, double replacedLon,
            List<PointSnap> points) {

        /** id всех мест, уже входящих в прогулку (их нельзя предлагать заменой) */
        public List<Long> currentPlaceIds() {
            List<Long> ids = new ArrayList<>();
            for (PointSnap s : points) {
                if (s.placeId() != null) {
                    ids.add(s.placeId());
                }
            }
            return ids;
        }
    }

    public record RerouteContext(boolean returnToStart, List<Long> unvisitedPlaceIds) {
    }
}
