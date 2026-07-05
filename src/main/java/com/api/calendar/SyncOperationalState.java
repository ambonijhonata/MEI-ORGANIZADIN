package com.api.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;

@Embeddable
public class SyncOperationalState {
    private static final String REVOKED_CATEGORY = "REVOKED";
    private static final SyncStatus INITIAL_STATUS = SyncStatus.NEVER_SYNCED;
    private static final SyncStatus REAUTH_STATUS = SyncStatus.REAUTH_REQUIRED;
    private static final SyncStatus SYNCED_STATUS = SyncStatus.SYNCED;
    private static final SyncStatus FAILED_STATUS = SyncStatus.SYNC_FAILED;
    private static final SyncStatus SYNCING_STATUS = SyncStatus.SYNCING;

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

    protected SyncOperationalState() {
    }

    public void markSyncing() {
        status = SYNCING_STATUS;
    }

    public void clearSyncToken() {
        syncToken = emptyText();
    }

    public void markSynced(final String nextSyncToken) {
        syncToken = nextSyncToken;
        lastSyncAt = Instant.now();
        status = SYNCED_STATUS;
        errorCategory = emptyText();
        errorMessage = emptyText();
    }

    public void keepSyncedWithExistingToken(final String existingSyncToken, final Instant syncedAt) {
        lastSyncAt = syncedAt;
        status = SYNCED_STATUS;
        errorCategory = emptyText();
        errorMessage = emptyText();
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

    public boolean isReauthRequired() {
        return status == REAUTH_STATUS;
    }

    public boolean isSyncing() {
        return status == SYNCING_STATUS;
    }

    public SyncStateSnapshot snapshot() {
        return new SyncStateSnapshot(syncToken, status, lastSyncAt, errorCategory, errorMessage);
    }

    private static String emptyText() {
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
