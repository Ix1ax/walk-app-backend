-- Признак значимости места: есть ли у него курируемый сигнал (wikidata/wikipedia/
-- heritage в OSM) или это «якорная» категория (музей/храм/достопримечательность).
-- Селектор предпочитает notable-места, чтобы маршрут собирался из точек притяжения,
-- а не из случайных мемориальных табличек.
ALTER TABLE places ADD COLUMN notable boolean NOT NULL DEFAULT false;

-- Частичный индекс: выборка значимых мест в зоне
CREATE INDEX idx_places_notable ON places (notable) WHERE notable = true;
