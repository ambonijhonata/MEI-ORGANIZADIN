package com.api.calendar;

import com.api.google.GoogleCalendarSyncEvent;
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
public class CalendarSyncScopeReconciler {
    private static final Logger LOG = LoggerFactory.getLogger(CalendarSyncScopeReconciler.class);

    private final CalendarEventRepository eventRepository;

    public CalendarSyncScopeReconciler(final CalendarEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public CalendarScopeReconciliationResult reconcile(final Long userId,
                                                       final List<GoogleCalendarSyncEvent> googleEvents,
                                                       final CalendarSyncMutations mutations,
                                                       final boolean fullSync,
                                                       final String syncMode,
                                                       final LocalDate startDate) {
        final List<CalendarEvent> localScopedEvents = loadScopedEvents(userId, fullSync, startDate);
        final CalendarSyncMutations scopedMutations =
                withScopeReconciliation(mutations, googleEvents, localScopedEvents, syncMode);
        return new CalendarScopeReconciliationResult(
                scopedMutations,
                extractAdditionalDeletions(mutations.deletions(), scopedMutations.deletions())
        );
    }

    private List<CalendarEvent> loadScopedEvents(final Long userId,
                                                 final boolean fullSync,
                                                 final LocalDate startDate) {
        List<CalendarEvent> scopedEvents = List.of();
        if (fullSync) {
            scopedEvents = eventRepository.findGoogleBackedByUserId(userId);
        } else if (startDate != null) {
            final Instant startBoundary = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            scopedEvents = eventRepository.findGoogleBackedByUserIdAndEventStartGreaterThanEqual(userId, startBoundary);
        }
        return scopedEvents;
    }

    private CalendarSyncMutations withScopeReconciliation(final CalendarSyncMutations mutations,
                                                          final List<GoogleCalendarSyncEvent> googleEvents,
                                                          final List<CalendarEvent> localScopedEvents,
                                                          final String mode) {
        CalendarSyncMutations reconciled = mutations;
        if (localScopedEvents != null && !localScopedEvents.isEmpty()) {
            final Map<String, CalendarEvent> scopedByGoogleId = indexLocalScopedEvents(localScopedEvents);
            if (!scopedByGoogleId.isEmpty()) {
                final ScopeCleanup cleanup = detectScopeCleanup(mutations, googleEvents, scopedByGoogleId);
                logCleanup(mode, mutations, scopedByGoogleId, cleanup);
                reconciled = rebuildMutations(mutations, cleanup);
            }
        }
        return reconciled;
    }

    private ScopeCleanup detectScopeCleanup(final CalendarSyncMutations mutations,
                                            final List<GoogleCalendarSyncEvent> googleEvents,
                                            final Map<String, CalendarEvent> scopedByGoogleId) {
        final Set<String> activeIds = extractActiveGoogleEventIds(googleEvents);
        final List<CalendarEvent> mergedDeletions = new ArrayList<>(mutations.deletions());
        final Set<String> deletionIds = collectDeletionIds(mutations.deletions());
        int deletedCount = mutations.deleted();

        for (final Map.Entry<String, CalendarEvent> entry : scopedByGoogleId.entrySet()) {
            final String googleId = entry.getKey();
            if (!activeIds.contains(googleId) && !deletionIds.contains(googleId)) {
                mergedDeletions.add(entry.getValue());
                deletionIds.add(googleId);
                deletedCount++;
            }
        }
        return new ScopeCleanup(mergedDeletions, deletedCount, activeIds.size());
    }

    private void logCleanup(final String mode,
                            final CalendarSyncMutations mutations,
                            final Map<String, CalendarEvent> scopedByGoogleId,
                            final ScopeCleanup cleanup) {
        if (cleanup.deletedCount() > mutations.deleted() && LOG.isInfoEnabled()) {
            LOG.info(
                    "calendar_sync_cleanup_summary mode={} cleanup_deleted={} marker_deleted={} total_deleted={} scoped_local_google_events={} active_google_events={}",
                    mode,
                    cleanup.deletedCount() - mutations.deleted(),
                    mutations.deleted(),
                    cleanup.deletedCount(),
                    scopedByGoogleId.size(),
                    cleanup.activeCount()
            );
        }
    }

    private CalendarSyncMutations rebuildMutations(final CalendarSyncMutations mutations, final ScopeCleanup cleanup) {
        return new CalendarSyncMutations(
                mutations.upserts(),
                cleanup.deletions(),
                mutations.serviceLinkReplacementEventIds(),
                mutations.created(),
                mutations.updated(),
                cleanup.deletedCount()
        );
    }

    private Map<String, CalendarEvent> indexLocalScopedEvents(final List<CalendarEvent> localScopedEvents) {
        final Map<String, CalendarEvent> scopedByGoogleId = new HashMap<>();
        for (final CalendarEvent localEvent : localScopedEvents) {
            if (localEvent != null && localEvent.isGoogleOrigin()) {
                scopedByGoogleId.put(localEvent.getGoogleEventId(), localEvent);
            }
        }
        return scopedByGoogleId;
    }

    private Set<String> extractActiveGoogleEventIds(final List<GoogleCalendarSyncEvent> googleEvents) {
        final Set<String> activeIds = new HashSet<>();
        if (googleEvents != null) {
            for (final GoogleCalendarSyncEvent googleEvent : googleEvents) {
                if (googleEvent != null
                        && googleEvent.hasUsableId()
                        && !googleEvent.isCancelled()) {
                    activeIds.add(googleEvent.googleEventId());
                }
            }
        }
        return activeIds;
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
                                                           final List<CalendarEvent> scopedDeletions) {
        List<CalendarEvent> additional = List.of();
        if (scopedDeletions != null && !scopedDeletions.isEmpty()) {
            final Set<String> seenIds = collectDeletionIds(baseDeletions == null ? List.of() : baseDeletions);
            additional = new ArrayList<>();
            for (final CalendarEvent deletion : scopedDeletions) {
                if (deletion != null && deletion.isGoogleOrigin() && !seenIds.contains(deletion.getGoogleEventId())) {
                    seenIds.add(deletion.getGoogleEventId());
                    additional.add(deletion);
                }
            }
        }
        return additional;
    }

    private record ScopeCleanup(List<CalendarEvent> deletions, int deletedCount, int activeCount) {
    }
}
