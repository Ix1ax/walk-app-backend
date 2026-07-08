package dev.walk.backend.features.auth.application;

/**
 * @author Ilya Samsonov
 * Аутентифицированный пользователь в {@code SecurityContext}. Кладётся principal'ом
 * после проверки JWT; в контроллерах достаётся через {@code @AuthenticationPrincipal}
 */
public record UserPrincipal(Long id, String email) {
}
