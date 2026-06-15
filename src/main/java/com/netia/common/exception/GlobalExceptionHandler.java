package com.netia.common.exception;

import com.netia.common.dto.ErrorResponse;
import com.netia.common.util.RequestIdHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.stream.Collectors;

/**
 * Centralised exception → HTTP response mapping.
 *
 * WHY @RestControllerAdvice (not per-controller @ExceptionHandler):
 *   A single global handler guarantees consistent error response format across all
 *   endpoints. Per-controller handlers would require duplication and risk divergence.
 *
 * WHY the errorResponse() helper method:
 *   Every handler builds the same ErrorResponse structure. The helper eliminates
 *   that duplication (DRY) and keeps each handler to a single readable line.
 *
 * Handler order matters — Spring picks the most specific matching type first,
 * so the generic Exception handler at the bottom only fires when nothing else matches.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex) {
        log.warn("Authentication error: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Brak poprawnego Bearer tokenu.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Użytkownik nie ma uprawnień do wykonania tej operacji.");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {} — {}", ex.getErrorCode(), ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
        // WHY stream().map().collect(): collects all field errors into one readable message
        // rather than surfacing only the first violation.
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Bean validation error: {}", message);
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        log.warn("API error: {} — {}", ex.getErrorCode(), ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ex.getErrorCode(), ex.getMessage());
    }

    /**
     * WHY 409 Conflict for optimistic locking (not 500):
     *   @Version on TroubleTicket triggers this when two concurrent PATCH requests race.
     *   409 is semantically correct: the request itself was valid but conflicts with the
     *   current resource state. The client should retry after a fresh GET.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLocking(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, "CONFLICT",
                "Zasób został zmodyfikowany przez inny proces. Pobierz aktualny stan i spróbuj ponownie.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Wewnętrzny błąd serwera.");
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * WHY a private helper instead of inlining ErrorResponse.builder() in each handler:
     *   DRY — the builder chain is identical in every handler. The helper also ensures
     *   requestId is always included without each handler remembering to add it.
     */
    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(
                ErrorResponse.builder()
                        .code(code)
                        .message(message)
                        .requestId(RequestIdHolder.getRequestId())
                        .build()
        );
    }
}
