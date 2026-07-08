package dev.walk.backend.features.auth.application;

import dev.walk.backend.common.exception.ConflictException;
import dev.walk.backend.common.exception.UnauthorizedException;
import dev.walk.backend.features.auth.api.dto.AuthResponse;
import dev.walk.backend.features.auth.api.dto.LoginRequest;
import dev.walk.backend.features.auth.api.dto.RegisterRequest;
import dev.walk.backend.features.auth.domain.User;
import dev.walk.backend.features.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Ilya Samsonov
 * Регистрация и вход по email+паролю. Пароль хешируется bcrypt'ом, в ответ выдаётся
 * JWT. Проверки почты (подтверждение) пока нет — регистрация сразу активна
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Пользователь с таким email уже существует");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // Гонка: параллельная регистрация того же email успела раньше
            throw new ConflictException("Пользователь с таким email уже существует");
        }

        log.info("Зарегистрирован пользователь id={}, email={}", user.getId(), email);
        return new AuthResponse(jwtService.generate(user), user.getId(), email);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalize(request.email());
        User user = userRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new UnauthorizedException("Неверный email или пароль"));
        return new AuthResponse(jwtService.generate(user), user.getId(), email);
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
