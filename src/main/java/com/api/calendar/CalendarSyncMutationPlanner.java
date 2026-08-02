package com.api.calendar;

import com.api.google.GoogleCalendarSyncEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@SuppressWarnings({
        "PMD.LongVariable",
        "PMD.OnlyOneReturn",
        "PMD.CognitiveComplexity",
        "PMD.CyclomaticComplexity"
})
public class CalendarSyncMutationPlanner {

    private final CalendarSyncExistingEventResolver existingEventResolver;
    private final CalendarSyncLookupResolver lookupResolver;
    private final CalendarSyncNewEventPlanner newEventPlanner;
    private final CalendarSyncExistingEventPlanner existingEventPlanner;

    public CalendarSyncMutationPlanner(final CalendarSyncLookupResolver lookupResolver,
                                       final CalendarSyncExistingEventResolver existingEventResolver,
                                       final CalendarSyncNewEventPlanner newEventPlanner,
                                       final CalendarSyncExistingEventPlanner existingEventPlanner) {
        this.lookupResolver = lookupResolver;
        this.existingEventResolver = existingEventResolver;
        this.newEventPlanner = newEventPlanner;
        this.existingEventPlanner = existingEventPlanner;
    }

    public CalendarSyncLookups buildLookups(final Long userId) {
        return lookupResolver.buildLookups(userId);
    }

    public CalendarSyncMutations processChunk(final CalendarSyncChunkRequest request) {
        if (request.googleEvents() == null || request.googleEvents().isEmpty()) {
            return CalendarSyncMutations.empty();
        }

        final Map<String, CalendarEvent> existingEventsByGoogleEventId =
                existingEventResolver.loadExistingEventsByGoogleEventId(
                        request.userId(),
                        request.googleEvents(),
                        request.fullSync()
                );
        final Map<Long, Map<String, Integer>> existingServiceIdentitiesByEventId =
                existingEventResolver.loadServiceIdentityByEventId(existingEventsByGoogleEventId.values());

        final List<CalendarEventMutationPlan> upserts = new ArrayList<>(request.googleEvents().size());
        final List<CalendarEvent> deletions = new ArrayList<>();
        int created = 0;
        int updated = 0;
        int deleted = 0;

        for (final GoogleCalendarSyncEvent googleEvent : request.googleEvents()) {
            if (!isUsableGoogleEvent(googleEvent)) {
                continue;
            }

            final CalendarEvent existingEvent = existingEventsByGoogleEventId.get(googleEvent.googleEventId());
            if (isDeletedEvent(googleEvent)) {
                if (existingEvent != null && request.allowDeletes()) {
                    deletions.add(existingEvent);
                    deleted++;
                }
                continue;
            }

            final CalendarEventMutationPlan mutationPlan = processEvent(
                    request.userId(),
                    request.user(),
                    googleEvent,
                    existingEvent,
                    request.lookups(),
                    existingEvent != null
                            ? existingServiceIdentitiesByEventId.getOrDefault(existingEvent.getId(), Map.of())
                            : Map.of(),
                    request.normCache()
            );
            if (mutationPlan.shouldPersist()) {
                upserts.add(mutationPlan);
            }
            if (mutationPlan.isNew()) {
                created++;
            } else if (mutationPlan.shouldPersist()) {
                updated++;
            }
        }

        return new CalendarSyncMutations(
                upserts,
                deletions,
                replacementEventIds(upserts),
                created,
                updated,
                deleted
        );
    }

    private CalendarEventMutationPlan processEvent(final Long userId,
                                                   final com.api.user.ApplicationUser user,
                                                   final GoogleCalendarSyncEvent googleEvent,
                                                   final CalendarEvent existingEvent,
                                                   final CalendarSyncLookups lookups,
                                                   final Map<String, Integer> existingServiceIdentities,
                                                   final Map<String, String> normCache) {
        final String title = googleEvent.summary();
        final CalendarSyncResolvedEventDetails resolvedDetails = lookupResolver.resolveEventDetails(
                userId,
                user,
                title,
                lookups,
                normCache
        );

        if (existingEvent == null) {
            return newEventPlanner.plan(user, googleEvent, resolvedDetails);
        }

        return existingEventPlanner.plan(
                existingEvent,
                title,
                googleEvent.start(),
                googleEvent.end(),
                resolvedDetails,
                existingServiceIdentities
        );
    }

    private boolean isDeletedEvent(final GoogleCalendarSyncEvent event) {
        return event.isCancelled();
    }

    private boolean isUsableGoogleEvent(final GoogleCalendarSyncEvent googleEvent) {
        return googleEvent != null && googleEvent.hasUsableId();
    }

    private java.util.Set<Long> replacementEventIds(final List<CalendarEventMutationPlan> upserts) {
        final java.util.Set<Long> replacementIds = new java.util.HashSet<>();
        for (final CalendarEventMutationPlan mutationPlan : upserts) {
            if (mutationPlan.shouldReplaceServiceLinks()) {
                replacementIds.add(mutationPlan.calendarEvent().getId());
            }
        }
        return replacementIds;
    }
}
