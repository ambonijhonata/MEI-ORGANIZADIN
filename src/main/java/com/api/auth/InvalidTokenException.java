package com.api.auth;

public class InvalidTokenException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidTokenException(final String message) {
        super(message);
    }
}
