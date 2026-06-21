-- Интересные места (точки притяжения) для построения прогулки.
-- Наполняется read-through из Geoapify Places и кэшируется здесь.
CREATE TABLE places (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    city_id     BIGINT           REFERENCES cities(id),        -- nullable: город может быть не определён
    name        VARCHAR(255)     NOT NULL,
    category    VARCHAR(32)      NOT NULL,                     -- имя PlaceCategory (EnumType.STRING)
    lat         DOUBLE PRECISION NOT NULL,
    lon         DOUBLE PRECISION NOT NULL,
    -- Геометрию держим на стороне БД: считается из lat/lon, в сущности не маппится.
    geom        geometry(Point, 4326) GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lon, lat), 4326)) STORED,
    external_id TEXT,                                          -- Geoapify place_id для дедупа (бывает длинным, >200 симв)
    source      VARCHAR(32)      NOT NULL DEFAULT 'geoapify',
    created_at  TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- Пространственный индекс для поиска «места рядом» (ST_DWithin)
CREATE INDEX idx_places_geom ON places USING GIST (geom);
-- Дедуп по внешнему id (там, где он есть)
CREATE UNIQUE INDEX idx_places_external_id ON places (external_id) WHERE external_id IS NOT NULL;
CREATE INDEX idx_places_city ON places (city_id);
