package com.api.calendar;

import com.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "sync_state")
public class SyncState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long syncStateId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Embedded
    private SyncOperationalState syncOps = new SyncOperationalState();

    @Embedded
    private SyncCatalogEnrichmentState catalogEnrichment = new SyncCatalogEnrichmentState();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SyncState() {
    }

    public SyncState(final User user) {
        this.user = user;
    }

    @PrePersist
    @PreUpdate
    protected void updateTimestamps() {
        ensureStatesInitialized();
        final Instant currentTimestamp = Instant.now();
        if (createdAt == null) {
            createdAt = currentTimestamp;
        }
        updatedAt = currentTimestamp;
    }

    public SyncOperationalState operationalState() {
        ensureStatesInitialized();
        return syncOps;
    }

    public SyncCatalogEnrichmentState catalogEnrichmentState() {
        ensureStatesInitialized();
        return catalogEnrichment;
    }

    private void ensureStatesInitialized() {
        if (syncOps == null) {
            syncOps = new SyncOperationalState();
        }
        if (catalogEnrichment == null) {
            catalogEnrichment = new SyncCatalogEnrichmentState();
        }
    }
}
