package dev.walk.backend.features.walk.domain;

/**
 * @author Ilya Samsonov
 * Статус сохранённой прогулки: активная (по ней ещё гуляют) или завершённая
 * (все точки посещены либо пользователь отметил её законченной)
 */
public enum WalkStatus {
    ACTIVE,
    COMPLETED
}
