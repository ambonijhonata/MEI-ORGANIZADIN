package com.api.auth;

public final class RefreshTokenException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private static final String INVALID_CODE = "REFRESH_TOKEN_INVALID";
    private static final String INVALID_MESSAGE = "Refresh token is invalid";

    private final String code;

    private RefreshTokenException(final String code, final String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static RefreshTokenException fromStatus(final RefreshTokenService.RotationStatus status) {
        return switch (status) {
            case INVALID -> new RefreshTokenException(INVALID_CODE, INVALID_MESSAGE);
            case REVOKED -> new RefreshTokenException("REFRESH_TOKEN_REVOKED", "Refresh token is revoked");
            case REUSED -> new RefreshTokenException("REFRESH_TOKEN_REUSED", "Refresh token reuse detected");
            case EXPIRED -> new RefreshTokenException("REFRESH_TOKEN_EXPIRED", "Refresh token is expired");
            case SUCCESS, RETRY_SAFE_SUCCESS -> new RefreshTokenException(INVALID_CODE, INVALID_MESSAGE);
        };
    }
}
