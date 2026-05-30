package com.api.common;
@SuppressWarnings("PMD.MissingSerialVersionUID")

public class InvalidPeriodException extends RuntimeException {
    public InvalidPeriodException(final String message) {
        super(message);
    }
}
