# Walk Backend

Идея: ...

**Стек:** Java 17 · Spring Boot 3.3 · Maven · PostgreSQL 16 + PostGIS · Flyway · Docker

## Быстрый старт (Docker)

```bash
cp .env.example .env        
docker compose up --build
```

Поднимется два контейнера: `walk-db` (PostGIS) и `walk-backend`.
Flyway автоматически применит миграции и включит PostGIS.

Проверка, что бэк живой:

```bash
curl http://localhost:8080/api/health
# {"name":"walk-backend","status":"UP"}
```

- Health: http://localhost:8080/actuator/health

