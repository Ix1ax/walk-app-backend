package dev.walk.backend.features.auth.application;

import dev.walk.backend.features.auth.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * @author Ilya Samsonov
 * Выпуск и разбор JWT (HS256). Subject токена — id пользователя, плюс клейм email.
 * Секрет и время жизни — из конфига {@code walk.security.jwt.*}
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(@Value("${walk.security.jwt.secret}") String secret,
                      @Value("${walk.security.jwt.ttl-hours}") long ttlHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofHours(ttlHours);
    }

    /** Выпускает токен для пользователя */
    public String generate(User user) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttl.toMillis());
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /**
     * Проверяет подпись/срок и возвращает принципала. Бросает исключение при любой
     * проблеме (невалидный/просроченный токен) — вызывающий фильтр трактует это как
     * отсутствие аутентификации
     */
    public UserPrincipal parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new UserPrincipal(Long.valueOf(claims.getSubject()), claims.get("email", String.class));
    }
}
