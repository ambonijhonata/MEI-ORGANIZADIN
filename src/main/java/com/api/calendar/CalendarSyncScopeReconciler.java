package com.api.calendar;

import com.google.api.services.calendar.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@SuppressWarnings({"PMD.LongVariable", "PMD.OnlyOneReturn", "PMD.LooseCoupling"})
public class CalendarSyncScopeReconciler {
    private static final Logger LOG = LoggerFactory.getLogger(CalendarSyncScopeReconciler.class);

    private final CalendarEventRepository eventRepository;

    public CalendarSyncScopeReconciler(final CalendarEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public CalendarScopeReconciliationResult reconcile(final Long userId,
                                                       final List<Event> googleEvents,
                                                       final CalendarSyncMutations mutations,
                                                       final boolean fullSync,
                                                       final String syncMode,
                                                       final LocalDate startDate) {
        final List<CalendarEvent> localScopedEvents = loadScopedEvents(userId, fullSync, startDate);
        final CalendarSyncMutations reconciledMutations =
                withScopeReconciliation(mutations, googleEvents, localScopedEvents, syncMode);
        return new CalendarScopeReconciliationResult(
                reconciledMutations,
                extractAdditionalDeletions(mutations.deletions(), reconciledMutations.deletions())
        );
    }

    private List<CalendarEvent> loadScopedEvents(final Long userId,
                                                 final boolean fullSync,
                                                 final LocalDate startDate) {
        if (fullSync) {
            return eventRepository.findGoogleBackedByUserId(userId);
        }
        if (startDate != null) {
            final Instant startDateBoundary = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            return eventRepository.findGoogleBackedByUserIdAndEventStartGreaterThanEqual(userId, startDateBoundary);
        }
        return List.of();
    }

    private CalendarSyncMutations withScopeReconciliation(final CalendarSyncMutations mutations,
                                                          final List<Event> googleEvents,
                                                          final List<CalendarEvent> localScopedEvents,
                                                          final String mode) {
        if (localScopedEvents == null || localScopedEvents.isEmpty()) {
            return mutations;
        }

        final Map<String, CalendarEvent> localScopedByGoogleEventId = indexLocalScopedEvents(localScopedEvents);
        if (localScopedByGoogleEventId.isEmpty()) {
            return mutations;
        }

        final Set<String> activeGoogleEventIds = extractActiveGoogleEventIds(googleEvents);
        final List<CalendarEvent> reconciledDeletions = new ArrayList<>(mutations.deletions());
        final Set<String> deletionIds = collectDeletionIds(mutations.deletions());
        int reconciledDeleted = mutations.deleted();

        for (final Map.Entry<String, CalendarEvent> entry : localScopedByGoogleEventId.entrySet()) {
            final String googleEventId = entry.getKey();
            if (!activeGoogleEventIds.contains(googleEventId) && !deletionIds.contains(googleEventId)) {
                reconciledDeletions.add(entry.getValue());
                deletionIds.add(googleEventId);
                reconciledDeleted++;
            }
        }

        if (reconciledDeleted > mutations.deleted() && LOG.isInfoEnabled()) {
            LOG.info(
                    "calendar_sync_cleanup_summary mode={} cleanup_deleted={} marker_deleted={} total_deleted={} scoped_local_google_events={} active_google_events={}",
                    mode,
                    reconciledDeleted - mutations.deleted(),
                    mutations.deleted(),
                    reconciledDeleted,
                    localScopedByGoogleEventId.size(),
                    activeGoogleEventIds.size()
            );
        }

        return new CalendarSyncMutations(
                mutations.upserts(),
                reconciledDeletions,
                mutations.serviceLinkReplacementEventIds(),
                mutations.created(),
                mutations.updated(),
                reconciledDeleted
        );
    }

    private Map<String, CalendarEvent> indexLocalScopedEvents(final List<CalendarEvent> localScopedEvents) {
        final Map<String, CalendarEvent> localScopedByGoogleEventId = new HashMap<>();
        for (final CalendarEvent localEvent : localScopedEvents) {
            if (localEvent != null && localEvent.isGoogleOrigin()) {
                localScopedByGoogleEventId.put(localEvent.getGoogleEventId(), localEvent);
            }
        }
        return localScopedByGoogleEventId;
    }

    private Set<String> extractActiveGoogleEventIds(final List<Event> googleEvents) {
        final Set<String> activeGoogleEventIds = new HashSet<>();
        if (googleEvents == null) {
            return activeGoogleEventIds;
        }
        for (final Event googleEvent : googleEvents) {
            if (googleEvent != null
                    && googleEvent.getId() != null
                    && !googleEvent.getId().isBlank()
                    && !"cancelled".equals(googleEvent.getStatus())) {
                activeGoogleEventIds.add(googleEvent.getId());
            }
        }
        return activeGoogleEventIds;
    }

    private Set<String> collectDeletionIds(final List<CalendarEvent> deletions) {
        final Set<String> deletionIds = new HashSet<>();
        for (final CalendarEvent deletion : deletions) {
            if (deletion != null && deletion.isGoogleOrigin()) {
                deletionIds.add(deletion.getGoogleEventId());
            }
        }
        return deletionIds;
    }

    private List<CalendarEvent> extractAdditionalDeletions(final List<CalendarEvent> baseDeletions,
                                                           final List<CalendarEvent> reconciledDeletions) {
        if (reconciledDeletions == null || reconciledDeletions.isEmpty()) {
            return List.of();
        }

        final Set<String> seenGoogleEventIds = collectDeletionIds(baseDeletions == null ? List.of() : baseDeletions);
        final List<CalendarEvent> additionalDeletions = new ArrayList<>();
        for (final CalendarEvent deletion : reconciledDeletions) {
            if (deletion != null && deletion.isGoogleOrigin() && !seenGoogleEventIds.contains(deletion.getGoogleEventId())) {
                seenGoogleEventIds.add(deletion.getGoogleEventId());
                additionalDeletions.add(deletion);
            }
        }
        return additionalDeletions;
    }
}
