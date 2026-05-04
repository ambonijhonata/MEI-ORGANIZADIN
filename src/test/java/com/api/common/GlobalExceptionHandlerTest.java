package com.api.common;

import com.api.auth.AuthController;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void shouldHandleInvalidTokenWith401() {
        var ex = new AuthController.InvalidTokenException("Invalid token");
        var response = handler.handleInvalidToken(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().status());
        assertEquals("INVALID_TOKEN", response.getBody().code());
        assertEquals("Invalid token", response.getBody().message());
        assertNotNull(response.getBody().timestamp());
    }

    @Test
    void shouldHandleOAuthExchangeWith502() {
        var ex = new AuthController.OAuthExchangeException("Exchange failed");
        var response = handler.handleOAuthExchange(ex);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals(502, response.getBody().status());
        assertEquals("OAUTH_EXCHANGE_FAILED", response.getBody().code());
    }

    @Test
    void shouldHandleRetryableRefreshWith503() {
        var ex = new AuthController.RefreshRetryableException("Refresh temporarily unavailable");
        var response = handler.handleRefreshRetryable(ex);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(503, response.getBody().status());
        assertEquals("REFRESH_RETRYABLE", response.getBody().code());
    }

    @Test
    void shouldHandleNotFoundWith404() {
        var ex = new ResourceNotFoundException("Not found");
        var response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().status());
        assertEquals("NOT_FOUND", response.getBody().code());
    }

    @Test
    void shouldHandleBusinessWith422() {
        var ex = new BusinessException("Business rule violated");
        var response = handler.handleBusiness(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals(422, response.getBody().status());
        assertEquals("BUSINESS_ERROR", response.getBody().code());
    }

    @Test
    void shouldHandleValidationWith400() {
        BindingResult bindingResult = mock(BindingResult.class);
        FieldError fieldError = new FieldError("object", "name", "must not be blank");
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError));

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);
        var response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().status());
        assertEquals("VALIDATION_ERROR", response.getBody().code());
        assertEquals(1, response.getBody().errors().size());
        assertEquals("name", response.getBody().errors().get(0).field());
        assertEquals("must not be blank", response.getBody().errors().get(0).message());
    }

    @Test
    void shouldHandleConstraintViolationWith400() {
        var ex = new ConstraintViolationException("constraint violated", Set.of());
        var response = handler.handleConstraintViolation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().status());
        assertEquals("VALIDATION_ERROR", response.getBody().code());
    }

    @Test
    void shouldHandleInvalidPeriodWith400() {
        var ex = new InvalidPeriodException("Period too long");
        var response = handler.handleInvalidPeriod(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().status());
        assertEquals("INVALID_PERIOD", response.getBody().code());
    }

    @Test
    void shouldHandleIntegrationRevokedWith403() {
        var ex = new IntegrationRevokedException("Integration revoked");
        var response = handler.handleIntegrationRevoked(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(403, response.getBody().status());
        assertEquals("INTEGRATION_REVOKED", response.getBody().code());
    }

    @Test
    void shouldHandleGoogleApiAccessDeniedWith403() {
        var ex = new GoogleApiAccessDeniedException("Calendar API is not enabled");
        var response = handler.handleGoogleApiAccessDenied(ex);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(403, response.getBody().status());
        assertEquals("GOOGLE_API_FORBIDDEN", response.getBody().code());
        assertEquals("Calendar API is not enabled", response.getBody().message());
    }
}
