package com.api.calendar;

import com.api.google.GoogleCalendarSyncEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@SuppressWarnings({
        "PMD.LongVariable",
        "PMD.OnlyOneReturn",
        "PMD.AvoidInstantiatingObjectsInLoops"
})
public class CalendarSyncExistingEventResolver {
    private static final int LARGE_INCREMENTAL_LOOKUP_THRESHOLD = 5000;

    private final CalendarEventRepository eventRepository;
    private final CalendarEventServiceLinkRepository serviceLinkRepository;
    private final CalendarSyncAssociationEvaluator associationEvaluator;

    public CalendarSyncExistingEventResolver(final CalendarEventRepository eventRepository,
                                             final CalendarEventServiceLinkRepository serviceLinkRepository,
                                             final CalendarSyncAssociationEvaluator associationEvaluator) {
        this.eventRepository = eventRepository;
        this.serviceLinkRepository = serviceLinkRepository;
        this.associationEvaluator = associationEvaluator;
    }

    public Map<String, CalendarEvent> loadExistingEventsByGoogleEventId(final Long userId,
                                                                        final List<GoogleCalendarSyncEvent> googleEvents,
                                                                        final boolean fullSync) {
        final Set<String> googleEventIds = extractGoogleEventIds(googleEvents);
        if (googleEventIds.isEmpty()) {
            return Map.of();
        }

        final List<CalendarEvent> existingEvents = fullSync || googleEventIds.size() > LARGE_INCREMENTAL_LOOKUP_THRESHOLD
                ? loadAllGoogleBackedEvents(userId)
                : loadScopedExistingEvents(userId, googleEventIds);

        if (existingEvents.isEmpty()) {
            return Map.of();
        }

        final Map<String, CalendarEvent> eventsByGoogleEventId = new HashMap<>();
        for (final CalendarEvent event : existingEvents) {
            if (event != null && event.isGoogleOrigin() && googleEventIds.contains(event.getGoogleEventId())) {
                eventsByGoogleEventId.put(event.getGoogleEventId(), event);
            }
        }
        return eventsByGoogleEventId;
    }

    public Map<Long, Map<String, Integer>> loadServiceIdentityByEventId(final Iterable<CalendarEvent> events) {
        final Map<Long, Map<String, Integer>> identitiesByEventId = new HashMap<>();
        if (events == null || serviceLinkRepository == null) {
            return identitiesByEventId;
        }

        final List<Long> eventIds = extractEventIds(events);
        if (eventIds.isEmpty()) {
            return identitiesByEventId;
        }

        appendLinkedRows(eventIds, identitiesByEventId);
        appendLegacyRows(eventIds, identitiesByEventId);

        for (final Long eventId : eventIds) {
            identitiesByEventId.computeIfAbsent(eventId, ignored -> new HashMap<>());
        }
        return identitiesByEventId;
    }

    private List<CalendarEvent> loadAllGoogleBackedEvents(final Long userId) {
        List<CalendarEvent> existingEvents = eventRepository.findAllWithAssociationsByUserId(userId);
        if (existingEvents == null || existingEvents.isEmpty()) {
            existingEvents = eventRepository.findGoogleBackedByUserId(userId);
        }
        return existingEvents == null ? List.of() : existingEvents;
    }

    private List<CalendarEvent> loadScopedExistingEvents(final Long userId, final Set<String> googleEventIds) {
        List<CalendarEvent> existingEvents =
                eventRepository.findWithAssociationsByUserIdAndGoogleEventIdIn(userId, googleEventIds);
        if (existingEvents == null || existingEvents.isEmpty()) {
            existingEvents = eventRepository.findByUserIdAndGoogleEventIdIn(userId, googleEventIds);
        }
        return existingEvents == null ? List.of() : existingEvents;
    }

    private Set<String> extractGoogleEventIds(final List<GoogleCalendarSyncEvent> googleEvents) {
        final Set<String> googleEventIds = new HashSet<>();
        if (googleEvents == null) {
            return googleEventIds;
        }
        for (final GoogleCalendarSyncEvent googleEvent : googleEvents) {
            if (googleEvent != null && googleEvent.hasUsableId()) {
                googleEventIds.add(googleEvent.googleEventId());
            }
        }
        return googleEventIds;
    }

    private List<Long> extractEventIds(final Iterable<CalendarEvent> events) {
        final List<Long> eventIds = new ArrayList<>();
        for (final CalendarEvent event : events) {
            if (event != null && event.getId() != null) {
                eventIds.add(event.getId());
            }
        }
        return eventIds;
    }

    private void appendLinkedRows(final List<Long> eventIds, final Map<Long, Map<String, Integer>> identitiesByEventId) {
        final List<CalendarEventServiceLinkRepository.ServiceIdentityRow> linkedRows =
                serviceLinkRepository.findServiceIdentityRowsByCalendarEventIdIn(eventIds);
        if (linkedRows == null) {
            return;
        }
        for (final CalendarEventServiceLinkRepository.ServiceIdentityRow row : linkedRows) {
            if (row != null && row.getCalendarEventId() != null) {
                incrementIdentityCount(
                        identitiesByEventId.computeIfAbsent(row.getCalendarEventId(), ignored -> new HashMap<>()),
                        associationEvaluator.serviceIdentity(
                                row.getServiceId(),
                                row.getServiceNormalizedDescription(),
                                row.getServiceDescription(),
                                row.getServiceValue()
                        )
                );
            }
        }
    }

    private void appendLegacyRows(final List<Long> eventIds, final Map<Long, Map<String, Integer>> identitiesByEventId) {
        final List<CalendarEventRepository.ServiceIdentityRow> legacyRows =
                eventRepository.findLegacyServiceIdentityRowsByCalendarEventIdIn(eventIds);
        if (legacyRows == null) {
            return;
        }
        for (final CalendarEventRepository.ServiceIdentityRow row : legacyRows) {
            if (row != null && row.getCalendarEventId() != null) {
                incrementIdentityCount(
                        identitiesByEventId.computeIfAbsent(row.getCalendarEventId(), ignored -> new HashMap<>()),
                        associationEvaluator.serviceIdentity(
                                row.getServiceId(),
                                row.getServiceNormalizedDescription(),
                                row.getServiceDescription(),
                                row.getServiceValue()
                        )
                );
            }
        }
    }

    private void incrementIdentityCount(final Map<String, Integer> identityCounts, final String identity) {
        identityCounts.put(identity, identityCounts.getOrDefault(identity, 0) + 1);
    }
}
