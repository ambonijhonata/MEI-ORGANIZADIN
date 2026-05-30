package com.api.common;
@SuppressWarnings("PMD.MissingSerialVersionUID")

public class BusinessException extends RuntimeException {
    public BusinessException(final String message) {
        super(message);
    }
}
