package com.api.common;

public class InvalidRequestParameterException extends RuntimeException {

    private final String field;

    public InvalidRequestParameterException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
