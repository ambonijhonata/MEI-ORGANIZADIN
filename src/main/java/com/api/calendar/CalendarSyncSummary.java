package com.api.calendar;

record CalendarSyncSummary(
        String mode,
        int eventsReceived,
        int created,
        int updated,
        int deleted,
        long googleFetchMs,
        long dbLookupMs,
        long processingMs,
        long dbWriteMs,
        long totalMs,
        boolean fallback,
        boolean hadTokenBefore,
        boolean hasTokenAfter
) {}
