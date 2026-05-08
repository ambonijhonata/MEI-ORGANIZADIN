package com.api.calendar;

import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CalendarEventReprocessor {

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventServiceLinkRepository calendarEventServiceLinkRepository;
    private final CalendarEventServiceMatcher matcher;
    private final EventTitleParser titleParser;
    private final ServiceDescriptionNormalizer normalizer;
    private final SyncStateRepository syncStateRepository;
    private final UserScopedExecutionLock userScopedExecutionLock;

    public CalendarEventReprocessor(CalendarEventRepository calendarEventRepository,
                                    CalendarEventServiceLinkRepository calendarEventServiceLinkRepository,
                                    CalendarEventServiceMatcher matcher,
                                    EventTitleParser titleParser,
                                    ServiceDescriptionNormalizer normalizer,
                                    SyncStateRepository syncStateRepository,
                                    UserScopedExecutionLock userScopedExecutionLock) {
        this.calendarEventRepository = calendarEventRepository;
        this.calendarEventServiceLinkRepository = calendarEventServiceLinkRepository;
        this.matcher = matcher;
        this.titleParser = titleParser;
        this.normalizer = normalizer;
        this.syncStateRepository = syncStateRepository;
        this.userScopedExecutionLock = userScopedExecutionLock;
    }

    @Async
    @Transactional
    public void reprocessUnidentifiedEvents(Long userId) {
        userScopedExecutionLock.execute(userId, () -> {
            List<CalendarEvent> unidentified = calendarEventRepository.findByUserIdAndIdentifiedFalse(userId);
            Map<String, Service> servicesByNormalizedDescription = matcher.servicesByNormalizedDescription(userId);
            Set<Long> replacementEventIds = new HashSet<>();

            for (CalendarEvent event : unidentified) {
                EventTitleParser.ParsedTitle parsed = titleParser.parse(event.getTitle());
                List<Service> matchedServices = resolveMatchedServices(parsed, servicesByNormalizedDescription);
                if (!matchedServices.isEmpty()) {
                    if (event.getId() != null) {
                        replacementEventIds.add(event.getId());
                    }
                    event.associateServices(matchedServices);
                }
                event.setPaymentType(parsed.paymentType());
            }

            if (!replacementEventIds.isEmpty()) {
                calendarEventServiceLinkRepository.deleteInBulkByCalendarEventIdIn(replacementEventIds);
                calendarEventServiceLinkRepository.flush();
            }
            calendarEventRepository.saveAll(unidentified);
            return null;
        });
    }

    @Async
    @Transactional
    public void enrichSynchronizedAppointments(Long userId) {
        userScopedExecutionLock.execute(userId, () -> {
            SyncState syncState = syncStateRepository.findByUserId(userId).orElse(null);
            enrichSynchronizedAppointmentsInternal(userId, syncState, true);
            return null;
        });
    }

    @Transactional
    public boolean enrichPendingSynchronizedAppointments(Long userId, SyncState syncState) {
        return enrichSynchronizedAppointmentsInternal(userId, syncState, false);
    }

    private List<Service> resolveMatchedServices(EventTitleParser.ParsedTitle parsed,
                                                 Map<String, Service> servicesByNormalizedDescription) {
        if (parsed.serviceNames().isEmpty()) {
            return List.of();
        }

        List<Service> matchedServices = new ArrayList<>(parsed.serviceNames().size());
        for (String serviceName : parsed.serviceNames()) {
            String normalizedServiceName = normalizer.normalize(serviceName);
            Service service = servicesByNormalizedDescription.get(normalizedServiceName);
            if (service != null) {
                matchedServices.add(service);
            }
        }
        return matchedServices;
    }

    private boolean enrichSynchronizedAppointmentsInternal(Long userId,
                                                           SyncState syncState,
                                                           boolean allowUntrackedBackfill) {
        long targetRevision = resolveTargetRevision(syncState, allowUntrackedBackfill);
        if (targetRevision == 0L) {
            return false;
        }

        List<CalendarEvent> synchronizedEvents = calendarEventRepository.findAllWithAssociationsByUserId(userId);
        Map<String, Service> servicesByNormalizedDescription = matcher.servicesByNormalizedDescription(userId);
        List<CalendarEvent> changedEvents = new ArrayList<>();

        for (CalendarEvent event : synchronizedEvents) {
            if (event.getGoogleEventId() == null || event.getGoogleEventId().isBlank()) {
                continue;
            }

            EventTitleParser.ParsedTitle parsed = titleParser.parse(event.getTitle());
            List<Service> matchedServices = resolveMatchedServices(parsed, servicesByNormalizedDescription);
            boolean changed = false;
            if (!matchedServices.isEmpty()) {
                changed = event.enrichServices(matchedServices);
            }

            if (event.getPaymentType() != parsed.paymentType()) {
                event.setPaymentType(parsed.paymentType());
                changed = true;
            }

            if (changed) {
                changedEvents.add(event);
            }
        }

        if (!changedEvents.isEmpty()) {
            calendarEventRepository.saveAll(changedEvents);
        }

        if (syncState != null && targetRevision > 0L) {
            syncState.markCatalogEnrichmentApplied(targetRevision);
            syncStateRepository.save(syncState);
        }
        return true;
    }

    private long resolveTargetRevision(SyncState syncState, boolean allowUntrackedBackfill) {
        if (syncState == null) {
            return allowUntrackedBackfill ? 1L : 0L;
        }
        if (syncState.hasPendingCatalogEnrichment()) {
            return syncState.getCatalogEnrichmentRevisionRequested();
        }
        return allowUntrackedBackfill && syncState.getCatalogEnrichmentRevisionRequested() == 0L ? 1L : 0L;
    }
}
