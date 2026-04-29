package com.api.auth;

public record RefreshTokenMetadata(
        String deviceId,
        String appVersion,
        String createdIp,
        String createdUserAgent
) {
    public static RefreshTokenMetadata empty() {
        return new RefreshTokenMetadata(null, null, null, null);
    }
}
