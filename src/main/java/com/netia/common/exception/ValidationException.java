package com.netia.common.exception;

/**
 * Thrown for business rule violations in request data (e.g. invalid status value).
 * Always maps to HTTP 400 Bad Request via GlobalExceptionHandler.
 *
 * WHY a dedicated exception (not MethodArgumentNotValidException):
 *   Bean Validation (@NotBlank, @NotNull) catches structural field errors at the
 *   controller layer. ValidationException is for semantic / domain-level rules that
 *   Bean Validation cannot express — e.g. "status must be 'new' on create" or
 *   "status must be 'closed' on PATCH". These rules live in the service layer.
 */
public class ValidationException extends ApiException {
    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }

    public ValidationException(String message, Throwable cause) {
        super("VALIDATION_ERROR", message, cause);
    }
}

