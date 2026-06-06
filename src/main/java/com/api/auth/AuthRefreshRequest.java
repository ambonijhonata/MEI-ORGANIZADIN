package com.api.auth;

import jakarta.validation.constraints.NotBlank;

public record AuthRefreshRequest(
        @NotBlank String refreshToken,
        String deviceId,
        String appVersion
) {
    public RefreshTokenMetadata metadataOrEmpty() {
        return new RefreshTokenMetadata(deviceId, appVersion, null, null);
    }
}
