package com.api.common;

import com.api.auth.AuthController;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(AuthController.RefreshTokenException.class)
    public ResponseEntity<ErrorResponse> handleRefreshToken(AuthController.RefreshTokenException ex) {
        var error = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                ex.getCode(),
                ex.getMessage(),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(AuthController.RefreshRetryableException.class)
    public ResponseEntity<ErrorResponse> handleRefreshRetryable(AuthController.RefreshRetryableException ex) {
        var error = new ErrorResponse(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                "REFRESH_RETRYABLE",
                ex.getMessage(),
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
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

    @ExceptionHandler(InvalidRequestParameterException.class)
    public ResponseEntity<ValidationErrorResponse> handleInvalidRequestParameter(InvalidRequestParameterException ex) {
        var error = new ValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Validation failed",
                List.of(new FieldError(ex.getField(), ex.getMessage())),
                Instant.now()
        );
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ValidationErrorResponse> handleArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String field = ex.getName() != null ? ex.getName() : "request";
        var error = new ValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Validation failed",
                List.of(new FieldError(field, field + " has an invalid value")),
                Instant.now()
        );
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ValidationErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "request has an invalid value";
        if (message.contains("Page index")) {
            return ResponseEntity.badRequest().body(new ValidationErrorResponse(
                    HttpStatus.BAD_REQUEST.value(),
                    "VALIDATION_ERROR",
                    "Validation failed",
                    List.of(new FieldError("page", "page must be greater than or equal to 0")),
                    Instant.now()
            ));
        }
        if (message.contains("Page size")) {
            return ResponseEntity.badRequest().body(new ValidationErrorResponse(
                    HttpStatus.BAD_REQUEST.value(),
                    "VALIDATION_ERROR",
                    "Validation failed",
                    List.of(new FieldError("size", "size must be greater than or equal to 1")),
                    Instant.now()
            ));
        }
        throw ex;
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

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {
        log.error("api_request_failed error_type={}", ex.getClass().getSimpleName(), ex);
        var error = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_SERVER_ERROR",
                "Unexpected internal error while processing request.",
                Instant.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    public record ErrorResponse(int status, String code, String message, Instant timestamp) {}

    public record ValidationErrorResponse(int status, String code, String message,
                                           List<FieldError> errors, Instant timestamp) {}

    public record FieldError(String field, String message) {}
}
