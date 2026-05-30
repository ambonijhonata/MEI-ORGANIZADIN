package com.api.common;

public class InvalidRequestParameterException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String field;

    public InvalidRequestParameterException(final String field, final String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
