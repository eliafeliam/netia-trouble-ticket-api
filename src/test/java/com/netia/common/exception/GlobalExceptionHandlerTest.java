package com.netia.common.exception;

import com.netia.common.dto.ErrorResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("GlobalExceptionHandler — unit tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("AuthenticationException → 401 UNAUTHORIZED")
    void handleAuthentication_returns401() {
        AuthenticationException ex = mock(AuthenticationException.class);
        when(ex.getMessage()).thenReturn("bad token");

        ResponseEntity<ErrorResponse> resp = handler.handleAuthentication(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody().getCode()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("AccessDeniedException → 403 FORBIDDEN")
    void handleAccessDenied_returns403() {
        ResponseEntity<ErrorResponse> resp = handler.handleAccessDenied(new AccessDeniedException("no access"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().getCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("ResourceNotFoundException → 404 with correct code and message")
    void handleNotFound_returns404() {
        ResourceNotFoundException ex = new ResourceNotFoundException("TROUBLE_TICKET_NOT_FOUND", "nie istnieje");

        ResponseEntity<ErrorResponse> resp = handler.handleNotFound(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody().getCode()).isEqualTo("TROUBLE_TICKET_NOT_FOUND");
        assertThat(resp.getBody().getMessage()).isEqualTo("nie istnieje");
    }

    @Test
    @DisplayName("ValidationException → 400 VALIDATION_ERROR")
    void handleValidation_returns400() {
        ResponseEntity<ErrorResponse> resp = handler.handleValidation(new ValidationException("zły status"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(resp.getBody().getMessage()).contains("zły status");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException → 400 with all field errors joined")
    void handleBeanValidation_joinsAllFieldErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(br);
        when(br.getFieldErrors()).thenReturn(List.of(
                new FieldError("req", "externalId", "externalId is required"),
                new FieldError("req", "serviceId", "must be greater than 0")
        ));

        ResponseEntity<ErrorResponse> resp = handler.handleBeanValidation(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getCode()).isEqualTo("VALIDATION_ERROR");
        assertThat(resp.getBody().getMessage()).contains("externalId").contains("serviceId");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException single error → message contains field name")
    void handleBeanValidation_singleError_containsFieldName() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(br);
        when(br.getFieldErrors()).thenReturn(List.of(
                new FieldError("req", "description", "description is required")
        ));

        ResponseEntity<ErrorResponse> resp = handler.handleBeanValidation(ex);

        assertThat(resp.getBody().getMessage()).isEqualTo("description: description is required");
    }

    @Test
    @DisplayName("ObjectOptimisticLockingFailureException → 409 CONFLICT")
    void handleOptimisticLocking_returns409() {
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("TroubleTicket", "TT-1");

        ResponseEntity<ErrorResponse> resp = handler.handleOptimisticLocking(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().getCode()).isEqualTo("CONFLICT");
        assertThat(resp.getBody().getMessage()).contains("zmodyfikowany");
    }

    @Test
    @DisplayName("ApiException → 400 with custom error code")
    void handleApiException_returns400WithCustomCode() {
        ApiException ex = new ApiException("CUSTOM_CODE", "custom message") {};

        ResponseEntity<ErrorResponse> resp = handler.handleApiException(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().getCode()).isEqualTo("CUSTOM_CODE");
        assertThat(resp.getBody().getMessage()).isEqualTo("custom message");
    }

    @Test
    @DisplayName("generic Exception → 500 INTERNAL_ERROR")
    void handleGeneric_returns500() {
        ResponseEntity<ErrorResponse> resp = handler.handleGeneric(new RuntimeException("boom"));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().getCode()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    @DisplayName("every error response always has non-null code and message")
    void allHandlers_neverReturnNullCodeOrMessage() {
        assertThat(handler.handleGeneric(new RuntimeException()).getBody().getCode()).isNotNull();
        assertThat(handler.handleGeneric(new RuntimeException()).getBody().getMessage()).isNotNull();
        assertThat(handler.handleValidation(new ValidationException("x")).getBody().getCode()).isNotNull();
        assertThat(handler.handleNotFound(new ResourceNotFoundException("C", "m")).getBody().getCode()).isNotNull();
    }

    @ParameterizedTest(name = "exception type: {0}")
    @ValueSource(strings = {"boom", "null pointer", "connection refused", ""})
    @DisplayName("generic handler returns 500 regardless of exception message content")
    void handleGeneric_anyMessage_alwaysReturns500(String message) {
        ResponseEntity<ErrorResponse> resp = handler.handleGeneric(new RuntimeException(message));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
