package com.api.common;

public class GoogleApiAccessDeniedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public GoogleApiAccessDeniedException(final String message) {
        super(message);
    }
}
