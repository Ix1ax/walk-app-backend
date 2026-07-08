package dev.walk.backend.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.walk.backend.common.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * @author Ilya Samsonov
 * Возвращает 401 в едином формате {@link ApiError}, когда к защищённому эндпоинту
 * обратились без валидного JWT (иначе Spring Security отдал бы пустой 401/403)
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper mapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        ApiError body = ApiError.of(401, HttpStatus.UNAUTHORIZED.toString(),
                "Требуется авторизация", request.getRequestURI());
        mapper.writeValue(response.getWriter(), body);
    }
}
