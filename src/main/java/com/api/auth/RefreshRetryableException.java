package com.api.auth;

public class RefreshRetryableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RefreshRetryableException(final String message) {
        super(message);
    }

    public RefreshRetryableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
