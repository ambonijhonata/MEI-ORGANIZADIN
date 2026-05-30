package com.api.client;

import com.api.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@SuppressWarnings({"PMD.CommentDefaultAccessModifier", "PMD.DataClass", "PMD.ShortVariable", "PMD.UseExplicitTypes"})
@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 500)
    private String normalizedName;

    @Column(length = 14)
    private String cpf;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Client() {}

    public Client(final User user, final String name, final String normalizedName) {
        this.user = user;
        this.name = name;
        this.normalizedName = normalizedName;
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
    public String getName() { return name; }
    public String getNormalizedName() { return normalizedName; }
    public String getCpf() { return cpf; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(final String name) { this.name = name; }
    public void setNormalizedName(final String normalizedName) { this.normalizedName = normalizedName; }
    public void setCpf(final String cpf) { this.cpf = cpf; }
    public void setDateOfBirth(final LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public void setEmail(final String email) { this.email = email; }
    public void setPhone(final String phone) { this.phone = phone; }
}
