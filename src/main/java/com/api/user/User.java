package com.api.user;

import jakarta.persistence.*;
import java.time.Instant;

@SuppressWarnings({"PMD.CommentDefaultAccessModifier", "PMD.DataClass", "PMD.ShortClassName", "PMD.ShortVariable", "PMD.UseExplicitTypes"})
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    protected User() {}

    public User(final String googleSub, final String email, final String name) {
        this.googleSub = googleSub;
        this.email = email;
        this.name = name;
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
    public String getGoogleSub() { return googleSub; }
    public String getEmail() { return email; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setEmail(final String email) { this.email = email; }
    public void setName(final String name) { this.name = name; }
}
