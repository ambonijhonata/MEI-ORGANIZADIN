package com.api.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.bind.Name;

@ConfigurationProperties(prefix = "auth.session")
public record SessionTokenProperties(
        @DefaultValue(DEFAULT_SECRET) String jwtSecret,
        @Name("access-token-ttl-seconds") @DefaultValue("900") long accessTtlSec,
        @Name("refresh-token-ttl-seconds") @DefaultValue("2592000") long refreshTtlSec,
        @Name("refresh-retry-safety-window-seconds") @DefaultValue("20") long retryWindowSec
) {
    private static final String DEFAULT_SECRET = "change-me-change-me-change-me-change-me";

    public SessionTokenProperties() {
        this(DEFAULT_SECRET, 900, 2_592_000, 20);
    }

    public Duration accessTtl() {
        return Duration.ofSeconds(accessTtlSec);
    }

    public Duration refreshTtl() {
        return Duration.ofSeconds(refreshTtlSec);
    }

    public long retryWindowSeconds() {
        return retryWindowSec;
    }
}
