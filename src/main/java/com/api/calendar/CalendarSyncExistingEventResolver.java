package com.api.calendar;

import com.api.google.GoogleCalendarSyncEvent;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CalendarSyncExistingEventResolver {

    private final CalendarSyncExistingEventLoader eventLoader;
    private final CalendarSyncServiceIdentityLoader identityLoader;

    public CalendarSyncExistingEventResolver(final CalendarEventRepository eventRepo,
                                             final CalendarEventServiceLinkRepository linkRepo,
                                             final CalendarSyncAssociationEvaluator assocEval) {
        this.eventLoader = new CalendarSyncExistingEventLoader(eventRepo);
        this.identityLoader = new CalendarSyncServiceIdentityLoader(eventRepo, linkRepo, assocEval);
    }

    public Map<String, CalendarEvent> loadExistingEventsByGoogleEventId(final Long userId,
                                                                        final List<GoogleCalendarSyncEvent> googleEvents,
                                                                        final boolean fullSync) {
        return eventLoader.loadByGoogleId(userId, googleEvents, fullSync);
    }

    public Map<Long, Map<String, Integer>> loadServiceIdentityByEventId(final Iterable<CalendarEvent> events) {
        return identityLoader.loadByEventId(events);
    }
}
