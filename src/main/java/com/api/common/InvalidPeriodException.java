package com.api.common;

public class InvalidPeriodException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public InvalidPeriodException(final String message) {
        super(message);
    }
}
