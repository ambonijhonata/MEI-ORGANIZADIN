package com.api.servicecatalog;

import com.api.user.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "services", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "normalized_description"})
})
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long serviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "normalized_description", nullable = false, length = 500)
    private String normalizedText;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal value;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Service() {}

    public Service(final User user, final String description, final String normalizedText, final BigDecimal value) {
        this.user = user;
        applyCatalogData(description, normalizedText, value);
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

    public final void updateCatalogData(final String newDesc,
                                        final String normText,
                                        final BigDecimal newValue) {
        applyCatalogData(newDesc, normText, newValue);
    }

    public final void rename(final String newDesc, final String normText) {
        this.description = newDesc;
        this.normalizedText = normText;
    }

    public final void reprice(final BigDecimal newValue) {
        this.value = newValue;
    }

    public boolean belongsTo(final Long userId) {
        return user != null && user.getId() != null && user.getId().equals(userId);
    }

    public boolean sameIdAs(final Long serviceKey) {
        return Objects.equals(serviceId, serviceKey);
    }

    public boolean hasNormalizedText(final String normText) {
        return normalizedText != null && normalizedText.equals(normText);
    }

    public Long getId() { return serviceId; }
    public User getUser() { return user; }
    public String getDescription() { return description; }
    public String getNormalizedDescription() { return normalizedText; }
    public BigDecimal getValue() { return value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    private void applyCatalogData(final String newDesc,
                                  final String normText,
                                  final BigDecimal newValue) {
        rename(newDesc, normText);
        reprice(newValue);
    }
}
