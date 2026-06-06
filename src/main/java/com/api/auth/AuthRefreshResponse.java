package com.api.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record AuthRefreshResponse(
        String accessToken,
        @JsonProperty("accessTokenExpiresAt") Instant accessExpiresAt,
        String refreshToken,
        @JsonProperty("refreshTokenExpiresAt") Instant refreshExpiresAt
) {}
