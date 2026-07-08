package dev.walk.backend.common.exception;

/**
 * @author Ilya Samsonov
 * Нарушение бизнес-правила при текущем состоянии ресурса (HTTP 409): например,
 * замена уже посещённой точки или перестроение прогулки, где все точки пройдены
 * {@link GlobalExceptionHandler}
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
