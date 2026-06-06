package com.api.common;

import com.api.auth.InvalidTokenException;
import com.api.auth.OAuthExchangeException;
import com.api.auth.RefreshRetryableException;
import com.api.auth.RefreshTokenException;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String VALIDATION_CODE = "VALIDATION_ERROR";
    private static final String VALIDATION_MSG = "Validation failed";
    private static final String INTERNAL_CODE = "INTERNAL_SERVER_ERROR";
    private static final String INTERNAL_MESSAGE = "Unexpected internal error while processing request.";
    private static final String INVALID_SUFFIX = " has an invalid value";
    private static final String PAGE_FIELD = "page";
    private static final String SIZE_FIELD = "size";
    private static final String REQUEST_FIELD = "request";
    private static final String PAGE_INDEX_PREFIX = "Page index";
    private static final String PAGE_SIZE_PREFIX = "Page size";

    public GlobalExceptionHandler() {
        LOGGER.getName();
    }

    @ExceptionHandler({
            InvalidTokenException.class,
            OAuthExchangeException.class,
            RefreshTokenException.class,
            RefreshRetryableException.class
    })
    public ResponseEntity<ErrorResponse> handleAuthExceptions(final Exception exception) {
        final ResponseEntity<ErrorResponse> response;
        if (exception instanceof InvalidTokenException tokenEx) {
            response = errorResponse(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_TOKEN",
                    tokenEx.getMessage()
            );
        } else if (exception instanceof OAuthExchangeException oauthEx) {
            response = errorResponse(
                    HttpStatus.BAD_GATEWAY,
                    "OAUTH_EXCHANGE_FAILED",
                    oauthEx.getMessage()
            );
        } else if (exception instanceof RefreshRetryableException retryableEx) {
            response = errorResponse(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "REFRESH_RETRYABLE",
                    retryableEx.getMessage()
            );
        } else if (exception instanceof RefreshTokenException refreshEx) {
            response = errorResponse(
                    HttpStatus.UNAUTHORIZED,
                    refreshEx.getCode(),
                    refreshEx.getMessage()
            );
        } else {
            throw new IllegalStateException("Unsupported auth exception: " + exception.getClass().getName());
        }

        return response;
    }

    @ExceptionHandler({
            ResourceNotFoundException.class,
            BusinessException.class,
            InvalidPeriodException.class,
            IntegrationRevokedException.class,
            GoogleApiAccessDeniedException.class
    })
    public ResponseEntity<ErrorResponse> handleApplicationExceptions(final Exception exception) {
        final ResponseEntity<ErrorResponse> response;
        if (exception instanceof ResourceNotFoundException notFoundEx) {
            response = errorResponse(HttpStatus.NOT_FOUND, "NOT_FOUND", notFoundEx.getMessage());
        } else if (exception instanceof BusinessException businessEx) {
            response = errorResponse(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "BUSINESS_ERROR",
                    businessEx.getMessage()
            );
        } else if (exception instanceof InvalidPeriodException periodEx) {
            response = errorResponse(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PERIOD",
                    periodEx.getMessage()
            );
        } else if (exception instanceof IntegrationRevokedException integrationEx) {
            response = errorResponse(
                    HttpStatus.FORBIDDEN,
                    "INTEGRATION_REVOKED",
                    integrationEx.getMessage()
            );
        } else if (exception instanceof GoogleApiAccessDeniedException googleEx) {
            response = errorResponse(
                    HttpStatus.FORBIDDEN,
                    "GOOGLE_API_FORBIDDEN",
                    googleEx.getMessage()
            );
        } else {
            throw new IllegalStateException("Unsupported application exception: " + exception.getClass().getName());
        }

        return response;
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            InvalidRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ValidationErrorResponse> handleValidationExceptions(final Exception exception) {
        final ValidationErrorResponse response;
        if (exception instanceof MethodArgumentNotValidException validEx) {
            final List<FieldError> fieldErrors = validEx.getBindingResult().getFieldErrors().stream()
                    .map(fieldError -> new FieldError(fieldError.getField(), fieldError.getDefaultMessage()))
                    .toList();
            response = validationErrorResponse(fieldErrors);
        } else if (exception instanceof InvalidRequestParameterException invalidParamEx) {
            response = validationErrorResponse(List.of(
                    new FieldError(invalidParamEx.getField(), invalidParamEx.getMessage())
            ));
        } else if (exception instanceof MethodArgumentTypeMismatchException typeMismatchEx) {
            final String fieldName = typeMismatchEx.getName() != null
                    ? typeMismatchEx.getName()
                    : REQUEST_FIELD;
            response = validationErrorResponse(List.of(
                    new FieldError(fieldName, fieldName + INVALID_SUFFIX)
            ));
        } else if (exception instanceof IllegalArgumentException illegalArgEx) {
            response = resolveIllegalArgumentValidationError(illegalArgEx);
        } else {
            throw new IllegalStateException("Unsupported validation exception: " + exception.getClass().getName());
        }

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(final ConstraintViolationException exception) {
        return errorResponse(HttpStatus.BAD_REQUEST, VALIDATION_CODE, exception.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(final RuntimeException exception) {
        if (LOGGER.isErrorEnabled()) {
            LOGGER.error("api_request_failed error_type={}", exception.getClass().getSimpleName(), exception);
        }
        return errorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                INTERNAL_CODE,
                INTERNAL_MESSAGE
        );
    }

    private ResponseEntity<ErrorResponse> errorResponse(
            final HttpStatus status,
            final String code,
            final String message
    ) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                status.value(),
                code,
                message,
                Instant.now()
        ));
    }

    private ValidationErrorResponse validationErrorResponse(final List<FieldError> fieldErrors) {
        return new ValidationErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                VALIDATION_CODE,
                VALIDATION_MSG,
                fieldErrors,
                Instant.now()
        );
    }

    private ValidationErrorResponse resolveIllegalArgumentValidationError(final IllegalArgumentException exception) {
        final String message = exception.getMessage() != null ? exception.getMessage() : "request has an invalid value";
        final ValidationErrorResponse response;
        if (message.contains(PAGE_INDEX_PREFIX)) {
            response = validationErrorResponse(List.of(
                    new FieldError(PAGE_FIELD, PAGE_FIELD + " must be greater than or equal to 0")
            ));
        } else if (message.contains(PAGE_SIZE_PREFIX)) {
            response = validationErrorResponse(List.of(
                    new FieldError(SIZE_FIELD, SIZE_FIELD + " must be greater than or equal to 1")
            ));
        } else {
            throw exception;
        }
        return response;
    }

    public record ErrorResponse(int status, String code, String message, Instant timestamp) {
    }

    public record ValidationErrorResponse(int status, String code, String message,
                                          List<FieldError> errors, Instant timestamp) {
    }

    public record FieldError(String field, String message) {
    }
}
