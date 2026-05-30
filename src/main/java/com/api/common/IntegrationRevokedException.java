package com.api.common;
@SuppressWarnings("PMD.MissingSerialVersionUID")

public class IntegrationRevokedException extends RuntimeException {
    public IntegrationRevokedException(final String message) {
        super(message);
    }
}
