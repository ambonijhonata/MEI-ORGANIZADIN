package com.api.calendar;

import com.api.google.GoogleCalendarSyncEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class CalendarSyncExistingEventLoader {
    private static final int LOOKUP_LIMIT = 5000;

    private final CalendarEventRepository eventRepo;

    /* package */ CalendarSyncExistingEventLoader(final CalendarEventRepository eventRepo) {
        this.eventRepo = eventRepo;
    }

    /* package */ Map<String, CalendarEvent> loadByGoogleId(final Long userId,
                                                            final List<GoogleCalendarSyncEvent> googleEvents,
                                                            final boolean fullSync) {
        final Set<String> googleIds = extractGoogleIds(googleEvents);
        final List<CalendarEvent> existingEvents = resolveExistingEvents(userId, fullSync, googleIds);
        return indexExistingEvents(existingEvents, googleIds);
    }

    private List<CalendarEvent> resolveExistingEvents(final Long userId,
                                                      final boolean fullSync,
                                                      final Set<String> googleIds) {
        List<CalendarEvent> existingEvents = List.of();
        if (!googleIds.isEmpty()) {
            final boolean useFullLookup = fullSync || googleIds.size() > LOOKUP_LIMIT;
            existingEvents = useFullLookup
                    ? loadAllGoogleBackedEvents(userId)
                    : loadScopedExistingEvents(userId, googleIds);
        }
        return existingEvents;
    }

    private List<CalendarEvent> loadAllGoogleBackedEvents(final Long userId) {
        List<CalendarEvent> existingEvents = eventRepo.findAllWithAssociationsByUserId(userId);
        if (existingEvents == null || existingEvents.isEmpty()) {
            existingEvents = eventRepo.findGoogleBackedByUserId(userId);
        }
        return existingEvents == null ? List.of() : existingEvents;
    }

    private List<CalendarEvent> loadScopedExistingEvents(final Long userId, final Set<String> googleIds) {
        List<CalendarEvent> existingEvents =
                eventRepo.findWithAssociationsByUserIdAndGoogleEventIdIn(userId, googleIds);
        if (existingEvents == null || existingEvents.isEmpty()) {
            existingEvents = eventRepo.findByUserIdAndGoogleEventIdIn(userId, googleIds);
        }
        return existingEvents == null ? List.of() : existingEvents;
    }

    private Map<String, CalendarEvent> indexExistingEvents(final List<CalendarEvent> existingEvents,
                                                           final Set<String> googleIds) {
        final Map<String, CalendarEvent> eventsByGoogleId = new HashMap<>();
        if (existingEvents != null && !existingEvents.isEmpty() && !googleIds.isEmpty()) {
            for (final CalendarEvent event : existingEvents) {
                if (event != null && event.isGoogleOrigin() && googleIds.contains(event.getGoogleEventId())) {
                    eventsByGoogleId.put(event.getGoogleEventId(), event);
                }
            }
        }
        return eventsByGoogleId;
    }

    private Set<String> extractGoogleIds(final List<GoogleCalendarSyncEvent> googleEvents) {
        final Set<String> googleIds = new HashSet<>();
        if (googleEvents != null) {
            for (final GoogleCalendarSyncEvent googleEvent : googleEvents) {
                if (googleEvent != null && googleEvent.hasUsableId()) {
                    googleIds.add(googleEvent.googleEventId());
                }
            }
        }
        return googleIds;
    }
}
