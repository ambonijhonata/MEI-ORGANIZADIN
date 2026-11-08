package com.api.google;

import java.time.Instant;

public record GoogleCalendarSyncEvent(
        String googleEventId,
        String summary,
        String status,
        Instant start,
        Instant end
) {

    private static final String UNTITLED_SUMMARY = "(Sem título)";

    public GoogleCalendarSyncEvent {
        summary = summary == null || summary.isBlank() ? UNTITLED_SUMMARY : summary;
    }

    public boolean hasUsableId() {
        return googleEventId != null && !googleEventId.isBlank();
    }

    public boolean isCancelled() {
        return "cancelled".equals(status);
    }
}
