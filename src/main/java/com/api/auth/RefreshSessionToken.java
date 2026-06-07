package com.api.auth;

import com.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "refresh_session_tokens")
public class RefreshSessionToken {
    @Id
    @Column(name = "id")
    private UUID tokenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 64)
    private String revokedReason;

    @Column(name = "replaced_by_token_id")
    private UUID replacementId;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Column(name = "app_version", length = 64)
    private String appVersion;

    @Column(name = "created_ip", length = 64)
    private String createdIp;

    @Column(name = "created_user_agent", length = 512)
    private String createdUserAgent;

    protected RefreshSessionToken() {
    }

    private RefreshSessionToken(
            final User user,
            final String tokenHash,
            final Instant issuedAt,
            final Instant expiresAt,
            final RefreshTokenMetadata metadata
    ) {
        this.tokenId = UUID.randomUUID();
        this.user = user;
        this.tokenHash = tokenHash;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.deviceId = metadata.deviceId();
        this.appVersion = metadata.appVersion();
        this.createdIp = metadata.createdIp();
        this.createdUserAgent = metadata.createdUserAgent();
    }

    public static RefreshSessionToken issue(
            final User user,
            final String tokenHash,
            final Instant issuedAt,
            final Instant expiresAt,
            final RefreshTokenMetadata metadata
    ) {
        return new RefreshSessionToken(user, tokenHash, issuedAt, expiresAt, metadata);
    }

    public boolean isExpired(final Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isReplaced() {
        return replacementId != null;
    }

    public void markReplacedBy(final UUID replacementId, final Instant now) {
        this.replacementId = replacementId;
        this.lastUsedAt = now;
        this.revokedAt = now;
        this.revokedReason = "ROTATED";
    }

    public void revoke(final String reason, final Instant now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
            this.revokedReason = reason;
        }
    }

    public UUID getId() {
        return tokenId;
    }

    public User getUser() {
        return user;
    }

    public Long getUserId() {
        return user == null ? null : user.getId();
    }

    public AuthenticatedUser toAuthenticatedUser() {
        return user.toAuthenticatedUser();
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevokedReason() {
        return revokedReason;
    }

    public UUID getReplacedByTokenId() {
        return replacementId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public String getCreatedIp() {
        return createdIp;
    }

    public String getCreatedUserAgent() {
        return createdUserAgent;
    }

    public boolean canRetrySafely(
            final String expectedReason,
            final Instant now,
            final long retryWindowSecs
    ) {
        boolean retrySafe = retryWindowSecs > 0
                && revokedAt != null
                && Objects.equals(expectedReason, revokedReason);
        if (retrySafe) {
            retrySafe = !now.isAfter(revokedAt.plusSeconds(retryWindowSecs));
        }
        return retrySafe;
    }
}
