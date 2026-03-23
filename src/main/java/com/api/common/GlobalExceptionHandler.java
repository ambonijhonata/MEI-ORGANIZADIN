package com.api.common;

import com.api.auth.AuthController;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthController.InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(AuthController.InvalidTokenException ex) {
        var error = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "INVALID_TOKEN",
                ex.getMessage(),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(AuthController.OAuthExchangeException.class)
    public ResponseEntity<ErrorResponse> handleOAuthExchange(AuthController.OAuthExchangeException ex) {
        var error = new ErrorResponse(
                HttpStatus.BAD_GATEWAY.value(),
                "OAUTH_EXCHANGE_FAILED",
                ex.getMessage(),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        var error = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                ex.getMessage(),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        var error = new ErrorResponse(
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                "BUSINESS_ERROR",
                ex.getMessage(),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        var error = new ValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Validation failed",
                fieldErrors,
                Instant.now()
        );
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        var error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                ex.getMessage(),
                Instant.now()
        );
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(InvalidPeriodException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPeriod(InvalidPeriodException ex) {
        var error = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "INVALID_PERIOD",
                ex.getMessage(),
                Instant.now()
        );
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(IntegrationRevokedException.class)
    public ResponseEntity<ErrorResponse> handleIntegrationRevoked(IntegrationRevokedException ex) {
        var error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "INTEGRATION_REVOKED",
                ex.getMessage(),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(GoogleApiAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleGoogleApiAccessDenied(GoogleApiAccessDeniedException ex) {
        var error = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "GOOGLE_API_FORBIDDEN",
                ex.getMessage(),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    public record ErrorResponse(int status, String code, String message, Instant timestamp) {}

    public record ValidationErrorResponse(int status, String code, String message,
                                           List<FieldError> errors, Instant timestamp) {}

    public record FieldError(String field, String message) {}
}
