package com.api.auth;

public class OAuthExchangeException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public OAuthExchangeException(final String message) {
        super(message);
    }

    public OAuthExchangeException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
