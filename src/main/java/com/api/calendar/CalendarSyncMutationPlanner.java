package com.api.calendar;

import com.api.google.GoogleCalendarSyncEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@SuppressWarnings({
        "PMD.LongVariable",
        "PMD.OnlyOneReturn"
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
        if (isEmptyChunk(request)) {
            return CalendarSyncMutations.empty();
        }

        final ExistingChunkState chunkState = loadExistingChunkState(request);
        final ChunkResultBuilder result = new ChunkResultBuilder(request.googleEvents().size());
        processGoogleEvents(request, chunkState, result);
        return result.toMutations();
    }

    private boolean isEmptyChunk(final CalendarSyncChunkRequest request) {
        return request.googleEvents() == null || request.googleEvents().isEmpty();
    }

    private ExistingChunkState loadExistingChunkState(final CalendarSyncChunkRequest request) {
        final Map<String, CalendarEvent> eventsByGoogleId = existingEventResolver.loadExistingEventsByGoogleEventId(
                request.userId(),
                request.googleEvents(),
                request.fullSync()
        );
        final Map<Long, Map<String, Integer>> serviceIdentityByEventId =
                existingEventResolver.loadServiceIdentityByEventId(eventsByGoogleId.values());
        return new ExistingChunkState(eventsByGoogleId, serviceIdentityByEventId);
    }

    private void processGoogleEvents(final CalendarSyncChunkRequest request,
                                     final ExistingChunkState chunkState,
                                     final ChunkResultBuilder result) {
        for (final GoogleCalendarSyncEvent googleEvent : request.googleEvents()) {
            processGoogleEvent(request, chunkState, result, googleEvent);
        }
    }

    private void processGoogleEvent(final CalendarSyncChunkRequest request,
                                    final ExistingChunkState chunkState,
                                    final ChunkResultBuilder result,
                                    final GoogleCalendarSyncEvent googleEvent) {
        if (!isUsableGoogleEvent(googleEvent)) {
            return;
        }

        final CalendarEvent existingEvent = chunkState.findExistingEvent(googleEvent.googleEventId());
        if (isDeletedEvent(googleEvent)) {
            handleDeletedEvent(request, result, existingEvent);
            return;
        }

        final CalendarEventMutationPlan mutationPlan = processEvent(
                request,
                googleEvent,
                existingEvent,
                chunkState.serviceIdentityOf(existingEvent),
                request.normCache()
        );
        result.recordMutation(mutationPlan);
    }

    private void handleDeletedEvent(final CalendarSyncChunkRequest request,
                                    final ChunkResultBuilder result,
                                    final CalendarEvent existingEvent) {
        if (existingEvent != null && request.allowDeletes()) {
            result.recordDeletion(existingEvent);
        }
    }

    private CalendarEventMutationPlan processEvent(final CalendarSyncChunkRequest request,
                                                   final GoogleCalendarSyncEvent googleEvent,
                                                   final CalendarEvent existingEvent,
                                                   final Map<String, Integer> existingServiceIdentities,
                                                   final Map<String, String> normCache) {
        final String title = googleEvent.summary();
        final CalendarSyncResolvedEventDetails resolvedDetails = resolveEventDetails(request, title, normCache);
        return planResolvedEvent(request, googleEvent, existingEvent, existingServiceIdentities, title, resolvedDetails);
    }

    private CalendarSyncResolvedEventDetails resolveEventDetails(final CalendarSyncChunkRequest request,
                                                                 final String title,
                                                                 final Map<String, String> normCache) {
        return lookupResolver.resolveEventDetails(
                request.userId(),
                request.user(),
                title,
                request.lookups(),
                normCache
        );
    }

    private CalendarEventMutationPlan planResolvedEvent(final CalendarSyncChunkRequest request,
                                                        final GoogleCalendarSyncEvent googleEvent,
                                                        final CalendarEvent existingEvent,
                                                        final Map<String, Integer> existingServiceIdentities,
                                                        final String title,
                                                        final CalendarSyncResolvedEventDetails resolvedDetails) {
        if (existingEvent == null) {
            return newEventPlanner.plan(request.user(), googleEvent, resolvedDetails);
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

    private record ExistingChunkState(Map<String, CalendarEvent> eventsByGoogleId,
                                      Map<Long, Map<String, Integer>> serviceIdentityByEventId) {

        private CalendarEvent findExistingEvent(final String googleEventId) {
            return eventsByGoogleId.get(googleEventId);
        }

        private Map<String, Integer> serviceIdentityOf(final CalendarEvent existingEvent) {
            Map<String, Integer> serviceIdentity = Map.of();
            if (existingEvent != null) {
                serviceIdentity = serviceIdentityByEventId.getOrDefault(existingEvent.getId(), Map.of());
            }
            return serviceIdentity;
        }
    }

    private final class ChunkResultBuilder {
        private final List<CalendarEventMutationPlan> upserts;
        private final List<CalendarEvent> deletions;
        private int created;
        private int updated;
        private int deleted;

        private ChunkResultBuilder(final int eventCount) {
            this.upserts = new ArrayList<>(eventCount);
            this.deletions = new ArrayList<>();
        }

        private void recordDeletion(final CalendarEvent existingEvent) {
            deletions.add(existingEvent);
            deleted++;
        }

        private void recordMutation(final CalendarEventMutationPlan mutationPlan) {
            if (mutationPlan.shouldPersist()) {
                upserts.add(mutationPlan);
            }
            if (mutationPlan.isNew()) {
                created++;
            } else if (mutationPlan.shouldPersist()) {
                updated++;
            }
        }

        private CalendarSyncMutations toMutations() {
            return new CalendarSyncMutations(
                    upserts,
                    deletions,
                    replacementEventIds(upserts),
                    created,
                    updated,
                    deleted
            );
        }
    }
}
