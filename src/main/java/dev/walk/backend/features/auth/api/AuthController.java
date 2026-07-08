package dev.walk.backend.features.auth.api;

import dev.walk.backend.features.auth.api.dto.AuthResponse;
import dev.walk.backend.features.auth.api.dto.LoginRequest;
import dev.walk.backend.features.auth.api.dto.RegisterRequest;
import dev.walk.backend.features.auth.application.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Ilya Samsonov
 * Регистрация и вход. Публичные эндпоинты — выдают JWT для доступа к защищённым
 * (сохранённые прогулки)
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
