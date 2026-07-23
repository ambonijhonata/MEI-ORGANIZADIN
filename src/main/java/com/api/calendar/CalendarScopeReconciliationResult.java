package com.api.calendar;

import java.util.List;

public record CalendarScopeReconciliationResult(
        CalendarSyncMutations mutations,
        List<CalendarEvent> extraDeletions
) {
}
