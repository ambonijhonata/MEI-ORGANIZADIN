package com.api.calendar;

import com.api.google.GoogleCalendarSyncEvent;
import com.api.user.ApplicationUser;
import org.springframework.stereotype.Component;

@Component
public record CalendarSyncNewEventPlanner() {

    public CalendarEventMutationPlan plan(final ApplicationUser user,
                                          final GoogleCalendarSyncEvent googleEvent,
                                          final CalendarSyncResolvedEventDetails resolvedDetails) {
        final CalendarEvent calendarEvent = new CalendarEvent(
                user,
                googleEvent.googleEventId(),
                googleEvent.summary(),
                resolvedDetails.normalizedTitle(),
                googleEvent.start(),
                googleEvent.end()
        );
        return CalendarEventMutationPlan.forNewEvent(
                calendarEvent,
                resolvedDetails.resolvedClient(),
                resolvedDetails.parsedTitle().hasClient(),
                resolvedDetails.parsedTitle().paymentType(),
                resolvedDetails.matchedServices()
        );
    }
}
