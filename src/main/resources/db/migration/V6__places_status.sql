-- Статус мест: скрытие закрытых/пожаловались и отметка «последний раз виден в Geoapify».
ALTER TABLE places ADD COLUMN hidden       BOOLEAN     NOT NULL DEFAULT false;  -- закрыто/пожаловались — не отдаём
ALTER TABLE places ADD COLUMN last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now();  -- для будущего пруна «призраков»

-- Выдача всегда фильтрует hidden — частичный индекс под это
CREATE INDEX idx_places_geom_visible ON places USING GIST (geom) WHERE hidden = false;
