package com.api.client;

import com.api.user.ApplicationUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long clientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private ApplicationUser user;

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

    public Client(final ApplicationUser user, final String name, final String normalizedName) {
        this.user = user;
        this.name = name;
        this.normalizedName = normalizedName;
    }

    public void rename(final String updatedName, final String normalized) {
        this.name = updatedName;
        this.normalizedName = normalized;
    }

    public void updatePersonalInfo(
            final String updatedCpf,
            final LocalDate birthDate,
            final String updatedEmail,
            final String updatedPhone) {
        this.cpf = updatedCpf;
        this.dateOfBirth = birthDate;
        this.email = updatedEmail;
        this.phone = updatedPhone;
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

    public boolean belongsTo(final Long userId) {
        return user != null && user.getId() != null && user.getId().equals(userId);
    }

    public boolean hasContactInfo() {
        return cpf != null || dateOfBirth != null || email != null || phone != null;
    }

    public boolean hasNormalizedName(final String normalized) {
        return normalizedName != null && normalizedName.equals(normalized);
    }

    public Long getId() { return clientId; }
    public ApplicationUser getUser() { return user; }
    public String getName() { return name; }
    public String getNormalizedName() { return normalizedName; }
    public String getCpf() { return cpf; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
