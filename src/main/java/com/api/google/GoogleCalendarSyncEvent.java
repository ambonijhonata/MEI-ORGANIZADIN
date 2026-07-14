package com.api.google;

import java.time.Instant;

public record GoogleCalendarSyncEvent(
        String googleEventId,
        String summary,
        String status,
        Instant start,
        Instant end
) {

    public boolean hasUsableId() {
        return googleEventId != null && !googleEventId.isBlank();
    }

    public boolean isCancelled() {
        return "cancelled".equals(status);
    }
}
