package com.api.calendar;

import com.api.user.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SyncStateTest {

    private final User user = new User("sub", "email@test.com", "Name");

    @Test
    void shouldInitializeWithNeverSyncedStatus() {
        SyncState syncState = new SyncState(user);
        CalendarIntegrationStatusReadModel readModel = CalendarIntegrationStatusMapper.toReadModel(syncState);

        assertEquals(SyncStatus.NEVER_SYNCED.name(), readModel.status());
        assertNull(syncState.snapshot().syncToken());
        assertNull(readModel.lastSyncAt());
        assertNull(readModel.errorCategory());
        assertNull(readModel.errorMessage());
        assertEquals(0L, syncState.resolveCatalogEnrichmentRevision(false));
        assertEquals(0L, syncState.resolveCatalogEnrichmentRevision(false));
    }

    @Test
    void shouldMarkSynced() {
        SyncState syncState = new SyncState(user);

        syncState.markSynced("token-123");
        CalendarIntegrationStatusReadModel readModel = CalendarIntegrationStatusMapper.toReadModel(syncState);

        assertEquals(SyncStatus.SYNCED.name(), readModel.status());
        assertEquals("token-123", syncState.snapshot().syncToken());
        assertNotNull(readModel.lastSyncAt());
        assertNull(readModel.errorCategory());
        assertNull(readModel.errorMessage());
    }

    @Test
    void shouldClearErrorsOnMarkSynced() {
        SyncState syncState = new SyncState(user);
        syncState.markFailed("IO_ERROR", "Connection failed");

        syncState.markSynced("token-456");

        assertEquals(SyncStatus.SYNCED.name(), CalendarIntegrationStatusMapper.toReadModel(syncState).status());
        assertNull(CalendarIntegrationStatusMapper.toReadModel(syncState).errorCategory());
        assertNull(CalendarIntegrationStatusMapper.toReadModel(syncState).errorMessage());
    }

    @Test
    void shouldMarkFailed() {
        SyncState syncState = new SyncState(user);

        syncState.markFailed("IO_ERROR", "Connection timeout");

        assertEquals(SyncStatus.SYNC_FAILED.name(), CalendarIntegrationStatusMapper.toReadModel(syncState).status());
        assertEquals("IO_ERROR", CalendarIntegrationStatusMapper.toReadModel(syncState).errorCategory());
        assertEquals("Connection timeout", CalendarIntegrationStatusMapper.toReadModel(syncState).errorMessage());
    }

    @Test
    void shouldMarkReauthRequired() {
        SyncState syncState = new SyncState(user);

        syncState.markReauthRequired("Token revoked by user");

        assertEquals(SyncStatus.REAUTH_REQUIRED.name(), CalendarIntegrationStatusMapper.toReadModel(syncState).status());
        assertEquals("REVOKED", CalendarIntegrationStatusMapper.toReadModel(syncState).errorCategory());
        assertEquals("Token revoked by user", CalendarIntegrationStatusMapper.toReadModel(syncState).errorMessage());
    }

    @Test
    void shouldAllowDomainTransitionsDirectly() {
        SyncState syncState = new SyncState(user);

        syncState.markFailed("CUSTOM", "Custom error");
        syncState.markSyncing();
        syncState.keepSyncedWithExistingToken("manual-token", java.time.Instant.now());

        assertEquals(SyncStatus.SYNCED.name(), CalendarIntegrationStatusMapper.toReadModel(syncState).status());
        assertEquals("manual-token", syncState.snapshot().syncToken());
        assertNull(CalendarIntegrationStatusMapper.toReadModel(syncState).errorCategory());
        assertNull(CalendarIntegrationStatusMapper.toReadModel(syncState).errorMessage());
    }

    @Test
    void shouldTrackPendingCatalogEnrichmentRevisions() {
        SyncState syncState = new SyncState(user);

        long requestedRevision = syncState.requestCatalogEnrichment();

        assertEquals(1L, requestedRevision);
        assertNotEquals(0L, syncState.resolveCatalogEnrichmentRevision(false));
        assertEquals(1L, syncState.resolveCatalogEnrichmentRevision(false));

        syncState.markCatalogEnrichmentApplied(requestedRevision);

        assertEquals(0L, syncState.resolveCatalogEnrichmentRevision(false));
        assertEquals(0L, syncState.resolveCatalogEnrichmentRevision(false));
    }

    @Test
    void shouldKeepNewerPendingRevisionWhenOlderOneIsApplied() {
        SyncState syncState = new SyncState(user);

        syncState.requestCatalogEnrichment();
        syncState.requestCatalogEnrichment();
        syncState.markCatalogEnrichmentApplied(1L);

        assertNotEquals(0L, syncState.resolveCatalogEnrichmentRevision(false));
        assertEquals(2L, syncState.resolveCatalogEnrichmentRevision(false));
    }

    @Test
    void shouldSeedBackfillRevisionWhenNoneWasRequestedYet() {
        SyncState syncState = new SyncState(user);

        assertEquals(1L, syncState.resolveCatalogEnrichmentRevision(true));
        assertNotEquals(0L, syncState.resolveCatalogEnrichmentRevision(true));
    }
}
