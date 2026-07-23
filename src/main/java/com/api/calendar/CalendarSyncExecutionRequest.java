package com.api.calendar;

import com.api.google.GoogleCalendarSyncEvent;
import com.api.user.ApplicationUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record CalendarSyncExecutionRequest(
        Long userId,
        ApplicationUser user,
        SyncState syncState,
        List<GoogleCalendarSyncEvent> googleEvents,
        boolean fullSync,
        boolean allowDeletes,
        String tokenBefore,
        String nextToken,
        String mode,
        LocalDate startDate,
        Map<String, String> normCache
) {
}
