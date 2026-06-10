package dev.walk.backend.common.exception;

/**
 * @author Ilya Samsonov
 * Общий exception для 404
 * {@link GlobalExceptionHandler}.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
