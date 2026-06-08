package com.api.calendar;

import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CalendarEventReprocessor {
    private static final long NO_REVISION = 0L;
    private static final long INITIAL_REVISION = 1L;

    private final CalendarEventRepository eventRepo;
    private final CalendarEventServiceMatcher matcher;
    private final CalendarEventReprocessingSupport support;
    private final SyncStateRepository stateRepo;
    private final UserScopedExecutionLock execLock;

    public CalendarEventReprocessor(final CalendarEventRepository eventRepository,
                                    final CalendarEventServiceLinkRepository linkRepo,
                                    final CalendarEventServiceMatcher matcher,
                                    final EventTitleParser eventParser,
                                    final ServiceDescriptionNormalizer descNorm,
                                    final SyncStateRepository syncRepo,
                                    final UserScopedExecutionLock scopedLock) {
        this.eventRepo = eventRepository;
        this.matcher = matcher;
        this.support = new CalendarEventReprocessingSupport(eventParser, descNorm);
        this.stateRepo = syncRepo;
        this.execLock = scopedLock;
    }

    @Async
    @Transactional
    public void reprocessUnidentifiedEvents(final Long userId) {
        execLock.execute(userId, () -> {
            final List<CalendarEvent> unidentified = eventRepo.findByUserIdAndIdentifiedFalse(userId);
            final Map<String, Service> servicesByName = matcher.servicesByNormalizedDescription(userId);

            for (final CalendarEvent event : unidentified) {
                support.applyParsedDetails(event, support.parseTitle(event), servicesByName, true);
            }

            eventRepo.saveAll(unidentified);
            return null;
        });
    }

    @Async
    @Transactional
    public void enrichSynchronizedAppointments(final Long userId) {
        execLock.execute(userId, () -> {
            final SyncState syncState = stateRepo.findByUserId(userId).orElse(null);
            enrichSynchronizedAppointmentsInternal(userId, syncState, true);
            return null;
        });
    }

    @Transactional
    public boolean enrichPendingSynchronizedAppointments(final Long userId, final SyncState syncState) {
        return enrichSynchronizedAppointmentsInternal(userId, syncState, false);
    }

    private boolean enrichSynchronizedAppointmentsInternal(final Long userId,
                                                           final SyncState syncState,
                                                           final boolean allowBackfill) {
        final long targetRevision = resolveTargetRevision(syncState, allowBackfill);
        boolean enriched = false;
        if (targetRevision != NO_REVISION) {
            enriched = true;
            final List<CalendarEvent> syncedEvents = eventRepo.findAllWithAssociationsByUserId(userId);
            final Map<String, Service> servicesByName = matcher.servicesByNormalizedDescription(userId);
            final List<CalendarEvent> changedEvents = new ArrayList<>();

            for (final CalendarEvent event : syncedEvents) {
                if (shouldProcess(event)
                        && support.applyParsedDetails(event, support.parseTitle(event), servicesByName, false)) {
                    changedEvents.add(event);
                }
            }

            if (!changedEvents.isEmpty()) {
                eventRepo.saveAll(changedEvents);
            }

            if (syncState != null) {
                syncState.markCatalogEnrichmentApplied(targetRevision);
                stateRepo.save(syncState);
            }
        }
        return enriched;
    }

    private long resolveTargetRevision(final SyncState syncState, final boolean allowBackfill) {
        long targetRevision = NO_REVISION;
        if (syncState == null) {
            targetRevision = allowBackfill ? INITIAL_REVISION : NO_REVISION;
        } else if (syncState.hasPendingCatalogEnrichment()) {
            targetRevision = syncState.getCatalogEnrichmentRevisionRequested();
        } else if (allowBackfill && syncState.getCatalogEnrichmentRevisionRequested() == NO_REVISION) {
            targetRevision = INITIAL_REVISION;
        }
        return targetRevision;
    }

    private boolean shouldProcess(final CalendarEvent event) {
        return event.isGoogleOrigin();
    }
}
