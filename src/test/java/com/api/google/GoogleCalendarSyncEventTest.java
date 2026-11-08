package com.api.google;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class GoogleCalendarSyncEventTest {

    @Test
    void shouldUseGoogleCalendarUntitledLabelWhenSummaryIsMissing() {
        GoogleCalendarSyncEvent event = new GoogleCalendarSyncEvent(
                "google-event-id",
                null,
                "confirmed",
                Instant.parse("2026-11-08T10:00:00Z"),
                Instant.parse("2026-11-08T11:00:00Z")
        );

        assertEquals("(Sem título)", event.summary());
    }
}
