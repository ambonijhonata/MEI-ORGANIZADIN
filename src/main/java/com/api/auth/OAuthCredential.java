package com.api.auth;

import com.api.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@SuppressWarnings({"PMD.CommentDefaultAccessModifier", "PMD.DataClass", "PMD.ShortVariable", "PMD.UseExplicitTypes"})
@Entity
@Table(name = "oauth_credentials")
public class OAuthCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Column(name = "refresh_token", nullable = false)
    private String refreshToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OAuthCredential() {}

    public OAuthCredential(final User user, final String accessToken, final String refreshToken, final Instant expiresAt) {
        this.user = user;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void prePersist() {
        final var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public Instant getExpiresAt() { return expiresAt; }

    public void setAccessToken(final String accessToken) { this.accessToken = accessToken; }
    public void setRefreshToken(final String refreshToken) { this.refreshToken = refreshToken; }
    public void setExpiresAt(final Instant expiresAt) { this.expiresAt = expiresAt; }
}
