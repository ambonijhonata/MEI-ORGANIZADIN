package com.api.calendar;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class CalendarSyncPersistenceSupport {

    private final CalendarEventRepository eventRepository;
    private final CalendarEventPaymentRepository paymentRepository;
    private final CalendarEventServiceLinkRepository linkRepository;
    private final CalendarSyncBatchSettings batchSettings;

    @PersistenceContext
    private EntityManager entityManager;

    public CalendarSyncPersistenceSupport(final CalendarEventRepository eventRepository,
                                          final CalendarEventPaymentRepository paymentRepository,
                                          final CalendarEventServiceLinkRepository linkRepository,
                                          final CalendarSyncBatchSettings batchSettings) {
        this.eventRepository = eventRepository;
        this.paymentRepository = paymentRepository;
        this.linkRepository = linkRepository;
        this.batchSettings = batchSettings;
    }

    public void setEntityManager(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void persistMutations(final CalendarSyncMutations mutations) {
        if (!mutations.replacementIds().isEmpty() && linkRepository != null) {
            linkRepository.deleteInBulkByCalendarEventIdIn(mutations.replacementIds());
            linkRepository.flush();
        }
        if (!mutations.deletions().isEmpty()) {
            final Set<Long> deletionEventIds = extractEventIds(mutations.deletions());
            if (!deletionEventIds.isEmpty() && paymentRepository != null) {
                paymentRepository.deleteInBulkByCalendarEventIdIn(deletionEventIds);
                paymentRepository.flush();
            }
            eventRepository.deleteAllInBatch(mutations.deletions());
        }
        if (!mutations.upserts().isEmpty()) {
            final List<CalendarEvent> eventsToPersist = new ArrayList<>(mutations.upserts().size());
            for (final CalendarEventMutationPlan mutationPlan : mutations.upserts()) {
                eventsToPersist.add(applyEventMutationPlan(mutationPlan));
            }
            saveEventsInBatches(eventsToPersist);
        }
    }

    private CalendarEvent applyEventMutationPlan(final CalendarEventMutationPlan mutationPlan) {
        final CalendarEvent calendarEvent = mutationPlan.event();
        if (mutationPlan.coreDataChanged()) {
            calendarEvent.updateFromGoogle(
                    mutationPlan.title(),
                    mutationPlan.normalizedTitle(),
                    mutationPlan.eventStart(),
                    mutationPlan.eventEnd()
            );
        }
        if (mutationPlan.clientChanged()) {
            calendarEvent.setClient(mutationPlan.resolvedClient());
        }
        if (mutationPlan.serviceAssociationChanged()) {
            if (mutationPlan.matchedServices().isEmpty()) {
                calendarEvent.clearServiceAssociation();
            } else {
                calendarEvent.associateServices(mutationPlan.matchedServices());
            }
        }
        if (mutationPlan.paymentTypeChanged()) {
            calendarEvent.setPaymentType(mutationPlan.paymentType());
        }
        return calendarEvent;
    }

    private Set<Long> extractEventIds(final List<CalendarEvent> events) {
        final Set<Long> eventIds = new HashSet<>();
        for (final CalendarEvent event : events) {
            if (event != null && event.getId() != null) {
                eventIds.add(event.getId());
            }
        }
        return eventIds;
    }

    private void saveEventsInBatches(final List<CalendarEvent> eventsToPersist) {
        final int chunkSize = Math.max(1, batchSettings.batchSize());
        int chunkCounter = 0;
        for (int startIndex = 0; startIndex < eventsToPersist.size(); startIndex += chunkSize) {
            final int endExclusive = Math.min(startIndex + chunkSize, eventsToPersist.size());
            eventRepository.saveAll(eventsToPersist.subList(startIndex, endExclusive));
            chunkCounter++;
            if (shouldFlush(chunkCounter, endExclusive, eventsToPersist.size())) {
                eventRepository.flush();
                if (batchSettings.batchClearEnabled() && entityManager != null) {
                    entityManager.clear();
                }
            }
        }
    }

    private boolean shouldFlush(final int chunkCounter, final int endExclusive, final int totalSize) {
        return (chunkCounter % batchSettings.flushInterval() == 0) || endExclusive == totalSize;
    }
}
