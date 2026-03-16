package com.api.servicecatalog;

import com.api.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "services", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "normalized_description"})
})
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "normalized_description", nullable = false, length = 500)
    private String normalizedDescription;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Service() {}

    public Service(User user, String description, String normalizedDescription, BigDecimal value) {
        this.user = user;
        this.description = description;
        this.normalizedDescription = normalizedDescription;
        this.value = value;
    }

    @PrePersist
    void prePersist() {
        var now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getDescription() { return description; }
    public String getNormalizedDescription() { return normalizedDescription; }
    public BigDecimal getValue() { return value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setDescription(String description) { this.description = description; }
    public void setNormalizedDescription(String normalizedDescription) { this.normalizedDescription = normalizedDescription; }
    public void setValue(BigDecimal value) { this.value = value; }
}
