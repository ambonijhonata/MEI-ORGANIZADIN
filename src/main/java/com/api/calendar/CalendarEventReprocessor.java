package com.api.calendar;

import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"PMD.AvoidLiteralsInIfCondition", "PMD.CyclomaticComplexity", "PMD.LawOfDemeter", "PMD.LongVariable", "PMD.OnlyOneReturn"})
@Component
public class CalendarEventReprocessor {

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventServiceLinkRepository calendarEventServiceLinkRepository;
    private final CalendarEventServiceMatcher matcher;
    private final EventTitleParser titleParser;
    private final ServiceDescriptionNormalizer normalizer;
    private final SyncStateRepository syncStateRepository;
    private final UserScopedExecutionLock userScopedExecutionLock;

    public CalendarEventReprocessor(final CalendarEventRepository calendarEventRepository,
                                    final CalendarEventServiceLinkRepository calendarEventServiceLinkRepository,
                                    final CalendarEventServiceMatcher matcher,
                                    final EventTitleParser titleParser,
                                    final ServiceDescriptionNormalizer normalizer,
                                    final SyncStateRepository syncStateRepository,
                                    final UserScopedExecutionLock userScopedExecutionLock) {
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
    public void reprocessUnidentifiedEvents(final Long userId) {
        userScopedExecutionLock.execute(userId, () -> {
            final List<CalendarEvent> unidentified = calendarEventRepository.findByUserIdAndIdentifiedFalse(userId);
            final Map<String, Service> servicesByNormalizedDescription = matcher.servicesByNormalizedDescription(userId);

            for (final CalendarEvent event : unidentified) {
                final EventTitleParser.ParsedTitle parsed = titleParser.parse(event.getLabel().getTitle());
                final List<Service> matchedServices = resolveMatchedServices(parsed, servicesByNormalizedDescription);
                if (!matchedServices.isEmpty()) {
                    event.associateServices(matchedServices);
                }
                event.setPaymentType(parsed.paymentType());
            }

            calendarEventRepository.saveAll(unidentified);
            return null;
        });
    }

    @Async
    @Transactional
    public void enrichSynchronizedAppointments(final Long userId) {
        userScopedExecutionLock.execute(userId, () -> {
            final SyncState syncState = syncStateRepository.findByUserId(userId).orElse(null);
            enrichSynchronizedAppointmentsInternal(userId, syncState, true);
            return null;
        });
    }

    @Transactional
    public boolean enrichPendingSynchronizedAppointments(final Long userId, final SyncState syncState) {
        return enrichSynchronizedAppointmentsInternal(userId, syncState, false);
    }

    private List<Service> resolveMatchedServices(final EventTitleParser.ParsedTitle parsed,
                                                 final Map<String, Service> servicesByNormalizedDescription) {
        if (parsed.serviceNames().isEmpty()) {
            return List.of();
        }

        final List<Service> matchedServices = new ArrayList<>(parsed.serviceNames().size());
        for (final String serviceName : parsed.serviceNames()) {
            final String normalizedServiceName = normalizer.normalize(serviceName);
            final Service service = servicesByNormalizedDescription.get(normalizedServiceName);
            if (service != null) {
                matchedServices.add(service);
            }
        }
        return matchedServices;
    }

    private boolean enrichSynchronizedAppointmentsInternal(final Long userId,
                                                           final SyncState syncState,
                                                           final boolean allowUntrackedBackfill) {
        final long targetRevision = resolveTargetRevision(syncState, allowUntrackedBackfill);
        if (targetRevision == 0L) {
            return false;
        }

        final List<CalendarEvent> synchronizedEvents = calendarEventRepository.findAllWithAssociationsByUserId(userId);
        final Map<String, Service> servicesByNormalizedDescription = matcher.servicesByNormalizedDescription(userId);
        final List<CalendarEvent> changedEvents = new ArrayList<>();

        for (final CalendarEvent event : synchronizedEvents) {
            if (!event.isGoogleOrigin()) {
                continue;
            }

            final EventTitleParser.ParsedTitle parsed = titleParser.parse(event.getLabel().getTitle());
            final List<Service> matchedServices = resolveMatchedServices(parsed, servicesByNormalizedDescription);
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

    private long resolveTargetRevision(final SyncState syncState, final boolean allowUntrackedBackfill) {
        if (syncState == null) {
            return allowUntrackedBackfill ? 1L : 0L;
        }
        if (syncState.hasPendingCatalogEnrichment()) {
            return syncState.getCatalogEnrichmentRevisionRequested();
        }
        return allowUntrackedBackfill && syncState.getCatalogEnrichmentRevisionRequested() == 0L ? 1L : 0L;
    }
}
