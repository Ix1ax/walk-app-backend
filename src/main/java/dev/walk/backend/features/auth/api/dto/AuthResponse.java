package dev.walk.backend.features.auth.api.dto;

/**
 * @author Ilya Samsonov
 * Ответ на регистрацию/вход: JWT для заголовка {@code Authorization: Bearer <token>}
 * и данные пользователя
 */
public record AuthResponse(
        String token,
        Long userId,
        String email
) {
}
