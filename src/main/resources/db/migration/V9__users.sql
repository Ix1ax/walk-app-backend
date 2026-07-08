-- Пользователи (этап 6): регистрация/логин по email+паролю, JWT.
-- Пароль хранится только как bcrypt-хеш.
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,   -- хранится в нижнем регистре
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Привязка сохранённой прогулки к пользователю (nullable: старые/анонимные прогулки).
-- История и проверка владельца теперь идут по user_id, а не по owner_token
ALTER TABLE walks ADD COLUMN user_id BIGINT REFERENCES users(id);
CREATE INDEX idx_walks_user_id ON walks (user_id) WHERE user_id IS NOT NULL;
