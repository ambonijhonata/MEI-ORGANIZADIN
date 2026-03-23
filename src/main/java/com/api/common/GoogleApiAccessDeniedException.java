package com.api.common;

public class GoogleApiAccessDeniedException extends RuntimeException {
    public GoogleApiAccessDeniedException(String message) {
        super(message);
    }
}
