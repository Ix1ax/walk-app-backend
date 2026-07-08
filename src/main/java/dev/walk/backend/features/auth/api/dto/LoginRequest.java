package dev.walk.backend.features.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @author Ilya Samsonov
 * Вход по email и паролю
 */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password
) {
}
