package com.api.google;

import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class GoogleCalendarTestEvents {

    private GoogleCalendarTestEvents() {
    }

    public static GoogleCalendarClient.CalendarSyncResult toSyncResult(final List<Event> googleEvents,
                                                                       final String nextSyncToken) {
        if (googleEvents == null || googleEvents.isEmpty()) {
            return new GoogleCalendarClient.CalendarSyncResult(List.of(), nextSyncToken);
        }

        final List<GoogleCalendarSyncEvent> snapshots = new ArrayList<>(googleEvents.size());
        for (final Event googleEvent : googleEvents) {
            if (googleEvent != null) {
                snapshots.add(new GoogleCalendarSyncEvent(
                        googleEvent.getId(),
                        googleEvent.getSummary(),
                        googleEvent.getStatus(),
                        extractInstant(googleEvent.getStart()),
                        extractInstant(googleEvent.getEnd())
                ));
            }
        }
        return new GoogleCalendarClient.CalendarSyncResult(snapshots, nextSyncToken);
    }

    private static Instant extractInstant(final EventDateTime eventDateTime) {
        Instant extractedInstant = Instant.now();
        if (eventDateTime != null) {
            if (eventDateTime.getDateTime() != null) {
                extractedInstant = Instant.ofEpochMilli(eventDateTime.getDateTime().getValue());
            } else if (eventDateTime.getDate() != null) {
                extractedInstant = Instant.ofEpochMilli(eventDateTime.getDate().getValue());
            }
        }
        return extractedInstant;
    }
}
