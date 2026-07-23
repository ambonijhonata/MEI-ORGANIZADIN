package com.api.calendar;

import java.time.Instant;

public record CalendarEventCoreUpdate(
        String title,
        String normalizedTitle,
        Instant start,
        Instant end,
        boolean changed
) {
    public static CalendarEventCoreUpdate current(final CalendarEvent event) {
        return new CalendarEventCoreUpdate(
                event.getTitle(),
                event.getNormalizedTitle(),
                event.getEventStart(),
                event.getEventEnd(),
                false
        );
    }

    public static CalendarEventCoreUpdate changed(final String title,
                                                  final String normalizedTitle,
                                                  final Instant start,
                                                  final Instant end,
                                                  final boolean changed) {
        return new CalendarEventCoreUpdate(title, normalizedTitle, start, end, changed);
    }
}
