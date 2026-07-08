-- Сохранённые прогулки (этап 5). Прогулка сохраняется вместе со своими точками:
-- по ней можно гулять (отмечать посещённые точки), заменить отдельную точку
-- и перестроить маршрут от нового местоположения, сохраняя те же точки.
CREATE TABLE walks (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_token           VARCHAR(128),                          -- анонимный владелец (id устройства), nullable до auth
    city_id               BIGINT           REFERENCES cities(id),
    start_lat             DOUBLE PRECISION NOT NULL,
    start_lon             DOUBLE PRECISION NOT NULL,
    return_to_start       BOOLEAN          NOT NULL DEFAULT true, -- кольцо (true) или открытый путь (false)
    duration_minutes      INTEGER          NOT NULL,             -- запрошенный бюджет времени (для реролла/перестроения)
    total_distance_meters BIGINT           NOT NULL,
    walk_minutes          BIGINT           NOT NULL,
    dwell_minutes         BIGINT           NOT NULL,
    total_minutes         BIGINT           NOT NULL,
    route_estimated       BOOLEAN          NOT NULL DEFAULT false, -- true = прямые линии (роутинг не сработал)
    route_json            TEXT,                                   -- геометрия+плечи маршрута (GeoRoute как JSON) для карты
    status                VARCHAR(24)      NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / COMPLETED (имя WalkStatus)
    created_at            TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- История прогулок по владельцу (устройству)
CREATE INDEX idx_walks_owner_token ON walks (owner_token) WHERE owner_token IS NOT NULL;
CREATE INDEX idx_walks_created_at ON walks (created_at DESC);

-- Точки прогулки в порядке обхода. Ключевые поля места снимаются снимком (name/
-- category/lat/lon/медиа), чтобы сохранённая прогулка была самодостаточной и не
-- ломалась, если место позже скрыли/удалили. place_id хранится для замены точки
-- (исключить уже вошедшие места) и связи с каталогом.
CREATE TABLE walk_points (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    walk_id               BIGINT           NOT NULL REFERENCES walks(id) ON DELETE CASCADE,
    place_id              BIGINT           REFERENCES places(id),
    seq                   INTEGER          NOT NULL,             -- порядковый номер в маршруте, с 1
    name                  VARCHAR(512)     NOT NULL,
    category              VARCHAR(32)      NOT NULL,             -- имя PlaceCategory (EnumType.STRING)
    lat                   DOUBLE PRECISION NOT NULL,
    lon                   DOUBLE PRECISION NOT NULL,
    leg_from_prev_meters  BIGINT           NOT NULL,             -- длина перехода от предыдущей точки/старта
    leg_from_prev_seconds BIGINT           NOT NULL,
    dwell_minutes         INTEGER          NOT NULL,
    image_url             TEXT,
    description           TEXT,
    info_url              TEXT,
    visited               BOOLEAN          NOT NULL DEFAULT false, -- отмечена пройденной (прогресс прогулки)
    visited_at            TIMESTAMPTZ,
    CONSTRAINT uq_walk_points_seq UNIQUE (walk_id, seq)
);

CREATE INDEX idx_walk_points_walk_id ON walk_points (walk_id);
