package com.api.common;

public class IntegrationRevokedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public IntegrationRevokedException(final String message) {
        super(message);
    }
}
