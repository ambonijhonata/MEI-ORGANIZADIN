package com.api.calendar;

public record CalendarSyncExecution(
        CalendarSyncService.SyncResult result,
        long dbLookupMs,
        long processingMs,
        long dbWriteMs
) {
}
