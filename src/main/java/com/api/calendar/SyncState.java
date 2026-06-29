package com.api.calendar;

import com.api.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@SuppressWarnings("PMD.TooManyMethods")
public class SyncState {
    private static final String REVOKED_CATEGORY = "REVOKED";
    private static final SyncStatus INITIAL_STATUS = SyncStatus.NEVER_SYNCED;
    private static final SyncStatus REAUTH_STATUS = SyncStatus.REAUTH_REQUIRED;
    private static final SyncStatus SYNCED_STATUS = SyncStatus.SYNCED;
    private static final SyncStatus FAILED_STATUS = SyncStatus.SYNC_FAILED;
    private static final SyncStatus SYNCING_STATUS = SyncStatus.SYNCING;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long syncStateId;

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

    @Embedded
    protected SyncCatalogEnrichmentState catalogEnrichment = new SyncCatalogEnrichmentState();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SyncState() {
    }

    public SyncState(final User user) {
        this.user = user;
        this.status = INITIAL_STATUS;
    }

    @PrePersist
    @PreUpdate
    protected void updateTimestamps() {
        ensureCatalogEnrichmentInitialized();
        final Instant currentTimestamp = Instant.now();
        if (createdAt == null) {
            createdAt = currentTimestamp;
        }
        updatedAt = currentTimestamp;
    }

    public void markSyncing() {
        status = SYNCING_STATUS;
    }

    public void clearSyncToken() {
        syncToken = noText();
    }

    public void markSynced(final String nextSyncToken) {
        syncToken = nextSyncToken;
        lastSyncAt = Instant.now();
        status = SYNCED_STATUS;
        errorCategory = noText();
        errorMessage = noText();
    }

    public void keepSyncedWithExistingToken(final String existingSyncToken, final Instant syncedAt) {
        lastSyncAt = syncedAt;
        status = SYNCED_STATUS;
        errorCategory = noText();
        errorMessage = noText();
        syncToken = existingSyncToken;
    }

    public void markFailed(final String category, final String message) {
        status = FAILED_STATUS;
        errorCategory = category;
        errorMessage = message;
    }

    public void markReauthRequired(final String reason) {
        status = REAUTH_STATUS;
        errorCategory = REVOKED_CATEGORY;
        errorMessage = reason;
    }

    public long requestCatalogEnrichment() {
        ensureCatalogEnrichmentInitialized();
        return catalogEnrichment.request();
    }

    public boolean isReauthRequired() {
        return status == REAUTH_STATUS;
    }

    public long resolveCatalogEnrichmentRevision(final boolean allowBackfill) {
        ensureCatalogEnrichmentInitialized();
        long revision = 0L;
        if (catalogEnrichment.hasPending()) {
            revision = catalogEnrichment.getRequestedRevision();
        } else if (allowBackfill && catalogEnrichment.getRequestedRevision() == 0L) {
            catalogEnrichment.ensureBackfillPending();
            revision = catalogEnrichment.getRequestedRevision();
        }
        return revision;
    }

    public void markCatalogEnrichmentApplied(final long revision) {
        ensureCatalogEnrichmentInitialized();
        catalogEnrichment.markApplied(revision);
    }

    public SyncStateSnapshot snapshot() {
        return new SyncStateSnapshot(syncToken, status, lastSyncAt, errorCategory, errorMessage);
    }

    private void ensureCatalogEnrichmentInitialized() {
        if (catalogEnrichment == null) {
            catalogEnrichment = new SyncCatalogEnrichmentState();
        }
    }

    private static String noText() {
        return null;
    }

    public record SyncStateSnapshot(
            String syncToken,
            SyncStatus status,
            Instant lastSyncAt,
            String errorCategory,
            String errorMessage
    ) {
    }
}
