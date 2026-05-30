package com.api.common;
@SuppressWarnings("PMD.MissingSerialVersionUID")

public class GoogleApiAccessDeniedException extends RuntimeException {
    public GoogleApiAccessDeniedException(final String message) {
        super(message);
    }
}
