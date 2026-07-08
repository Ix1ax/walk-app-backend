package dev.walk.backend.common.exception;

/**
 * @author Ilya Samsonov
 * Неверные учётные данные при входе (HTTP 401)
 * {@link GlobalExceptionHandler}
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
