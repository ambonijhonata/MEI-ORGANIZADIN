package com.api.user;

import com.api.auth.AuthenticatedUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "users")
public class ApplicationUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long userId;

    @Column(name = "google_sub", nullable = false, unique = true)
    private String googleSub;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ApplicationUser() {
    }

    public ApplicationUser(final String googleSub, final String email, final String name) {
        this.googleSub = googleSub;
        this.email = email;
        this.name = name;
    }

    @PrePersist
    protected void prePersist() {
        final Instant currentTimestamp = Instant.now();
        this.createdAt = currentTimestamp;
        this.updatedAt = currentTimestamp;
    }

    @PreUpdate
    protected void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public void updateProfile(final String emailAddress, final String displayName) {
        this.email = emailAddress;
        this.name = displayName;
    }

    public boolean matchesGoogleSub(final String subject) {
        return Objects.equals(googleSub, subject);
    }

    public boolean isIdentifiedBy(final Long candidateUserId) {
        return userId != null && userId.equals(candidateUserId);
    }

    public AuthenticatedUser toAuthenticatedUser() {
        return new AuthenticatedUser(userId, googleSub, email, name);
    }

    public Long getId() {
        return userId;
    }

    public String getGoogleSub() {
        return googleSub;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
