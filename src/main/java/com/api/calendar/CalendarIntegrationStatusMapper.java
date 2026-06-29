package com.api.calendar;

public final class CalendarIntegrationStatusMapper {
    private CalendarIntegrationStatusMapper() {
    }

    public static CalendarIntegrationStatusReadModel toReadModel(final SyncState syncState) {
        final SyncState.SyncStateSnapshot snapshot = syncState.snapshot();
        return new CalendarIntegrationStatusReadModel(
                snapshot.status() != null ? snapshot.status().name() : SyncStatus.NEVER_SYNCED.name(),
                snapshot.lastSyncAt() != null ? snapshot.lastSyncAt().toString() : null,
                snapshot.errorCategory(),
                snapshot.errorMessage()
        );
    }
}
