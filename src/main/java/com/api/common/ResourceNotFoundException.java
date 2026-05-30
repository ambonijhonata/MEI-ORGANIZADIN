package com.api.common;
@SuppressWarnings("PMD.MissingSerialVersionUID")

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(final String message) {
        super(message);
    }
}
