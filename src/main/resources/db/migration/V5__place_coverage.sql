-- Зоны, по которым уже тянули места из Geoapify
CREATE TABLE place_coverage (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cell_lat   INTEGER     NOT NULL,   -- round(lat * 100), ячейка ~1.1 км
    cell_lon   INTEGER     NOT NULL,   -- round(lon * 100)
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (cell_lat, cell_lon)
);
