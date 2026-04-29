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
import java.util.UUID;

@Entity
@Table(name = "refresh_session_tokens")
public class RefreshSessionToken {
    @Id
    private UUID id;

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
    private UUID replacedByTokenId;

    @Column(name = "device_id", length = 128)
    private String deviceId;

    @Column(name = "app_version", length = 64)
    private String appVersion;

    @Column(name = "created_ip", length = 64)
    private String createdIp;

    @Column(name = "created_user_agent", length = 512)
    private String createdUserAgent;

    public static RefreshSessionToken issue(
            User user,
            String tokenHash,
            Instant issuedAt,
            Instant expiresAt,
            RefreshTokenMetadata metadata
    ) {
        RefreshSessionToken token = new RefreshSessionToken();
        token.id = UUID.randomUUID();
        token.user = user;
        token.tokenHash = tokenHash;
        token.issuedAt = issuedAt;
        token.expiresAt = expiresAt;
        token.deviceId = metadata.deviceId();
        token.appVersion = metadata.appVersion();
        token.createdIp = metadata.createdIp();
        token.createdUserAgent = metadata.createdUserAgent();
        return token;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isReplaced() {
        return replacedByTokenId != null;
    }

    public void markReplacedBy(UUID replacementTokenId, Instant now) {
        this.replacedByTokenId = replacementTokenId;
        this.lastUsedAt = now;
        this.revokedAt = now;
        this.revokedReason = "ROTATED";
    }

    public void revoke(String reason, Instant now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
            this.revokedReason = reason;
        }
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
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
        return replacedByTokenId;
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
}
