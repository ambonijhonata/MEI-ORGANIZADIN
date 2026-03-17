package com.api.calendar;

import com.api.user.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SyncStateTest {

    private final User user = new User("sub", "email@test.com", "Name");

    @Test
    void shouldInitializeWithNeverSyncedStatus() {
        SyncState syncState = new SyncState(user);

        assertEquals(SyncStatus.NEVER_SYNCED, syncState.getStatus());
        assertNull(syncState.getSyncToken());
        assertNull(syncState.getLastSyncAt());
        assertNull(syncState.getErrorCategory());
        assertNull(syncState.getErrorMessage());
    }

    @Test
    void shouldMarkSynced() {
        SyncState syncState = new SyncState(user);

        syncState.markSynced("token-123");

        assertEquals(SyncStatus.SYNCED, syncState.getStatus());
        assertEquals("token-123", syncState.getSyncToken());
        assertNotNull(syncState.getLastSyncAt());
        assertNull(syncState.getErrorCategory());
        assertNull(syncState.getErrorMessage());
    }

    @Test
    void shouldClearErrorsOnMarkSynced() {
        SyncState syncState = new SyncState(user);
        syncState.markFailed("IO_ERROR", "Connection failed");

        syncState.markSynced("token-456");

        assertEquals(SyncStatus.SYNCED, syncState.getStatus());
        assertNull(syncState.getErrorCategory());
        assertNull(syncState.getErrorMessage());
    }

    @Test
    void shouldMarkFailed() {
        SyncState syncState = new SyncState(user);

        syncState.markFailed("IO_ERROR", "Connection timeout");

        assertEquals(SyncStatus.SYNC_FAILED, syncState.getStatus());
        assertEquals("IO_ERROR", syncState.getErrorCategory());
        assertEquals("Connection timeout", syncState.getErrorMessage());
    }

    @Test
    void shouldMarkReauthRequired() {
        SyncState syncState = new SyncState(user);

        syncState.markReauthRequired("Token revoked by user");

        assertEquals(SyncStatus.REAUTH_REQUIRED, syncState.getStatus());
        assertEquals("REVOKED", syncState.getErrorCategory());
        assertEquals("Token revoked by user", syncState.getErrorMessage());
    }

    @Test
    void shouldAllowSettingFieldsDirectly() {
        SyncState syncState = new SyncState(user);

        syncState.setStatus(SyncStatus.SYNCING);
        syncState.setSyncToken("manual-token");
        syncState.setErrorCategory("CUSTOM");
        syncState.setErrorMessage("Custom error");

        assertEquals(SyncStatus.SYNCING, syncState.getStatus());
        assertEquals("manual-token", syncState.getSyncToken());
        assertEquals("CUSTOM", syncState.getErrorCategory());
        assertEquals("Custom error", syncState.getErrorMessage());
    }
}
