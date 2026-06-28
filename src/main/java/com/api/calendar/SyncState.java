package com.api.calendar;

import com.api.user.User;
import jakarta.persistence.*;
import java.time.Instant;

@SuppressWarnings({
        "PMD.CommentDefaultAccessModifier",
        "PMD.LongVariable",
        "PMD.NullAssignment",
        "PMD.ShortVariable",
        "PMD.TooManyMethods",
        "PMD.UseExplicitTypes"
})
@Entity
@Table(name = "sync_state")
public class SyncState {
    private static final SyncStatus INITIAL_STATUS = SyncStatus.NEVER_SYNCED;
    private static final SyncStatus SYNCED_STATUS = SyncStatus.SYNCED;
    private static final SyncStatus FAILED_STATUS = SyncStatus.SYNC_FAILED;
    private static final SyncStatus REAUTH_REQUIRED_STATUS = SyncStatus.REAUTH_REQUIRED;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "sync_token")
    private String syncToken;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncStatus status = INITIAL_STATUS;

    @Column(name = "error_category")
    private String errorCategory;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "catalog_enrichment_revision_requested", nullable = false)
    private long catalogEnrichmentRevisionRequested;

    @Column(name = "catalog_enrichment_revision_applied", nullable = false)
    private long catalogEnrichmentRevisionApplied;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SyncState() {}

    public SyncState(final User user) {
        this.user = user;
        this.status = INITIAL_STATUS;
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
    public String getSyncToken() { return syncToken; }
    public Instant getLastSyncAt() { return lastSyncAt; }
    public SyncStatus getStatus() { return status; }
    public String getErrorCategory() { return errorCategory; }
    public String getErrorMessage() { return errorMessage; }
    public long getCatalogEnrichmentRevisionRequested() { return catalogEnrichmentRevisionRequested; }
    public long getCatalogEnrichmentRevisionApplied() { return catalogEnrichmentRevisionApplied; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setSyncToken(final String syncToken) { this.syncToken = syncToken; }
    public void setLastSyncAt(final Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public void setStatus(final SyncStatus status) { this.status = status; }
    public void setErrorCategory(final String errorCategory) { this.errorCategory = errorCategory; }
    public void setErrorMessage(final String errorMessage) { this.errorMessage = errorMessage; }
    public void setCatalogEnrichmentRevisionRequested(final long catalogEnrichmentRevisionRequested) {
        this.catalogEnrichmentRevisionRequested = Math.max(0L, catalogEnrichmentRevisionRequested);
    }
    public void setCatalogEnrichmentRevisionApplied(final long catalogEnrichmentRevisionApplied) {
        this.catalogEnrichmentRevisionApplied = Math.max(0L, catalogEnrichmentRevisionApplied);
    }

    public void markSynced(final String syncToken) {
        this.syncToken = syncToken;
        this.lastSyncAt = Instant.now();
        this.status = SYNCED_STATUS;
        this.errorCategory = null;
        this.errorMessage = null;
    }

    public void markFailed(final String errorCategory, final String errorMessage) {
        this.status = FAILED_STATUS;
        this.errorCategory = errorCategory;
        this.errorMessage = errorMessage;
    }

    public void markReauthRequired(final String reason) {
        this.status = REAUTH_REQUIRED_STATUS;
        this.errorCategory = "REVOKED";
        this.errorMessage = reason;
    }

    public long requestCatalogEnrichment() {
        this.catalogEnrichmentRevisionRequested = Math.max(
                this.catalogEnrichmentRevisionRequested + 1,
                this.catalogEnrichmentRevisionApplied + 1
        );
        return this.catalogEnrichmentRevisionRequested;
    }

    public boolean hasPendingCatalogEnrichment() {
        return this.catalogEnrichmentRevisionApplied < this.catalogEnrichmentRevisionRequested;
    }

    public boolean isReauthRequired() {
        return this.status == REAUTH_REQUIRED_STATUS;
    }

    public boolean isSyncing() {
        return this.status == SyncStatus.SYNCING;
    }

    public void markCatalogEnrichmentApplied(final long appliedRevision) {
        if (appliedRevision <= 0) {
            return;
        }
        this.catalogEnrichmentRevisionApplied = Math.max(
                this.catalogEnrichmentRevisionApplied,
                Math.min(appliedRevision, this.catalogEnrichmentRevisionRequested)
        );
    }

    public void ensureCatalogEnrichmentBackfillPending() {
        if (this.catalogEnrichmentRevisionRequested == 0) {
            this.catalogEnrichmentRevisionRequested = Math.max(1L, this.catalogEnrichmentRevisionApplied + 1);
        }
    }

    public boolean wasSyncedAfter(final Instant threshold) {
        return lastSyncAt != null && lastSyncAt.isAfter(threshold);
    }

    public CalendarIntegrationStatusReadModel toReadModel() {
        final String statusName = status != null ? status.name() : INITIAL_STATUS.name();
        return new CalendarIntegrationStatusReadModel(
                statusName,
                lastSyncAt != null ? lastSyncAt.toString() : null,
                errorCategory,
                errorMessage
        );
    }
}
