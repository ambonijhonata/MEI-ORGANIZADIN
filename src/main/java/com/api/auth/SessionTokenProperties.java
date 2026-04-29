package com.api.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auth.session")
public class SessionTokenProperties {
    private String jwtSecret = "change-me-change-me-change-me-change-me";
    private long accessTokenTtlSeconds = 900;
    private long refreshTokenTtlSeconds = 2_592_000;
    private long refreshRetrySafetyWindowSeconds = 20;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public long getRefreshRetrySafetyWindowSeconds() {
        return refreshRetrySafetyWindowSeconds;
    }

    public void setRefreshRetrySafetyWindowSeconds(long refreshRetrySafetyWindowSeconds) {
        this.refreshRetrySafetyWindowSeconds = refreshRetrySafetyWindowSeconds;
    }
}
