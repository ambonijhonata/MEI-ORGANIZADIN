package com.api.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record AuthLoginResponse(
        Long userId,
        String email,
        String name,
        String accessToken,
        @JsonProperty("accessTokenExpiresAt") Instant accessExpiresAt,
        String refreshToken,
        @JsonProperty("refreshTokenExpiresAt") Instant refreshExpiresAt
) {}
