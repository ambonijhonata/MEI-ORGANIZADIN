package com.api.auth;

import jakarta.validation.constraints.NotBlank;

public record AuthLoginRequest(
        @NotBlank String idToken,
        String authorizationCode,
        String deviceId,
        String appVersion
) {
    public RefreshTokenMetadata metadataOrEmpty() {
        return new RefreshTokenMetadata(deviceId, appVersion, null, null);
    }
}
