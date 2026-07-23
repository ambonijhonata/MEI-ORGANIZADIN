package com.api.calendar;

import com.api.google.GoogleCalendarSyncEvent;
import com.api.user.ApplicationUser;
import java.util.List;
import java.util.Map;

public record CalendarSyncChunkRequest(
        Long userId,
        ApplicationUser user,
        List<GoogleCalendarSyncEvent> googleEvents,
        CalendarSyncLookups lookups,
        boolean fullSync,
        boolean allowDeletes,
        Map<String, String> normCache
) {
}
