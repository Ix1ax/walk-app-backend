package dev.walk.backend.common.exception;

import java.time.Instant;
import java.util.Map;

/**
 * @author Ilya Samsonov
 * Единый формат тела ошибки, который возвращает API
 *
 * @param timestamp время возникновения ошибки
 * @param status    HTTP-код статуса
 * @param error     короткий машиночитаемый код ошибки
 * @param message   человекочитаемое описание
 * @param path      путь запроса, на котором произошла ошибка
 * @param fields    необязательные сообщения валидации по полям (может быть null)
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fields
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError validation(String message, String path, Map<String, String> fields) {
        return new ApiError(Instant.now(), 400, "VALIDATION_ERROR", message, path, fields);
    }
}
