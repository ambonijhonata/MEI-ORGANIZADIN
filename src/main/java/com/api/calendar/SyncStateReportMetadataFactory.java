package com.api.calendar;

import com.api.report.RevenueReportService;
import java.time.Instant;

public final class SyncStateReportMetadataFactory {
    private SyncStateReportMetadataFactory() {
    }

    public static RevenueReportService.SyncMetadata create(final SyncState syncState, final Instant threshold) {
        final SyncState.SyncStateSnapshot snapshot = syncState.snapshot();
        final boolean dataUpToDate = snapshot.lastSyncAt() != null && snapshot.lastSyncAt().isAfter(threshold);
        return new RevenueReportService.SyncMetadata(dataUpToDate, snapshot.lastSyncAt(), syncState.isReauthRequired());
    }
}
