-- Города, которые поддерживает приложение
CREATE TABLE cities (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name         VARCHAR(120)     NOT NULL,
    slug         VARCHAR(120)     NOT NULL UNIQUE,
    country_code VARCHAR(2)       NOT NULL DEFAULT 'RU',
    center_lat   DOUBLE PRECISION NOT NULL,
    center_lon   DOUBLE PRECISION NOT NULL,
    created_at   TIMESTAMPTZ      NOT NULL DEFAULT now()
);

-- Для поиска по названию без учёта регистра (ILIKE / lower())
CREATE INDEX idx_cities_name_lower ON cities (lower(name));
