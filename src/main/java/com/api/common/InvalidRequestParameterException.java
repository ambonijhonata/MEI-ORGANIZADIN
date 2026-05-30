package com.api.common;
@SuppressWarnings("PMD.MissingSerialVersionUID")

public class InvalidRequestParameterException extends RuntimeException {

    private final String field;

    public InvalidRequestParameterException(final String field, final String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
