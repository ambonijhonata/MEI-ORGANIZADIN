package com.api.calendar;

import com.api.client.Client;
import com.api.client.ClientService;
import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.user.User;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@SuppressWarnings({
        "PMD.LongVariable",
        "PMD.OnlyOneReturn",
        "PMD.LooseCoupling",
        "PMD.CouplingBetweenObjects",
        "PMD.CognitiveComplexity",
        "PMD.CyclomaticComplexity",
        "PMD.LawOfDemeter"
})
public class CalendarSyncMutationPlanner {

    private final ClientService clientService;
    private final CalendarEventServiceMatcher matcher;
    private final ServiceDescriptionNormalizer normalizer;
    private final EventTitleParser titleParser;
    private final CalendarSyncExistingEventResolver existingEventResolver;
    private final CalendarSyncAssociationEvaluator associationEvaluator;

    public CalendarSyncMutationPlanner(final ClientService clientService,
                                       final CalendarEventServiceMatcher matcher,
                                       final ServiceDescriptionNormalizer normalizer,
                                       final EventTitleParser titleParser,
                                       final CalendarSyncExistingEventResolver existingEventResolver,
                                       final CalendarSyncAssociationEvaluator associationEvaluator) {
        this.clientService = clientService;
        this.matcher = matcher;
        this.normalizer = normalizer;
        this.titleParser = titleParser;
        this.existingEventResolver = existingEventResolver;
        this.associationEvaluator = associationEvaluator;
    }

    public CalendarSyncLookups buildLookups(final Long userId) {
        return new CalendarSyncLookups(
                copyMap(clientService.listClientsByNormalizedName(userId)),
                copyMap(matcher.servicesByNormalizedDescription(userId))
        );
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

        for (final Event googleEvent : request.googleEvents()) {
            if (!isUsableGoogleEvent(googleEvent)) {
                continue;
            }

            final CalendarEvent existingEvent = existingEventsByGoogleEventId.get(googleEvent.getId());
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
                    request.lookups().clientsByNormalizedName(),
                    request.lookups().servicesByNormalizedDescription(),
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
                                                   final User user,
                                                   final Event googleEvent,
                                                   final CalendarEvent existingEvent,
                                                   final Map<String, Client> clientsByNormalizedName,
                                                   final Map<String, Service> servicesByNormalizedDescription,
                                                   final Map<String, Integer> existingServiceIdentities,
                                                   final Map<String, String> normCache) {
        final String googleEventId = googleEvent.getId();
        final String title = googleEvent.getSummary();
        final String normalizedTitle = normalizeWithCache(title, normCache);
        final Instant eventStart = extractInstant(googleEvent.getStart());
        final Instant eventEnd = extractInstant(googleEvent.getEnd());
        final EventTitleParser.ParsedTitle parsedTitle = titleParser.parse(title);
        final Client resolvedClient = resolveClient(
                userId,
                user,
                parsedTitle,
                clientsByNormalizedName,
                normCache
        );
        final List<Service> matchedServices =
                resolveMatchedServices(parsedTitle, servicesByNormalizedDescription, normCache);

        if (existingEvent == null) {
            final CalendarEvent calendarEvent =
                    new CalendarEvent(user, googleEventId, title, normalizedTitle, eventStart, eventEnd);
            return CalendarEventMutationPlan.forNewEvent(
                    calendarEvent,
                    resolvedClient,
                    parsedTitle.hasClient(),
                    parsedTitle.paymentType(),
                    matchedServices
            );
        }

        final boolean coreDataChanged =
                associationEvaluator.hasCoreDataChanges(existingEvent, title, normalizedTitle, eventStart, eventEnd);
        final boolean clientChanged = parsedTitle.hasClient()
                && !associationEvaluator.isEquivalentClient(existingEvent.getClient(), resolvedClient);
        final boolean serviceAssociationChanged = associationEvaluator.hasServiceAssociationChanges(
                existingEvent,
                matchedServices,
                existingServiceIdentities
        );
        final boolean paymentTypeChanged =
                !Objects.equals(existingEvent.getPaymentType(), parsedTitle.paymentType());
        final boolean shouldPersist =
                coreDataChanged || clientChanged || serviceAssociationChanged || paymentTypeChanged;

        if (!shouldPersist) {
            return CalendarEventMutationPlan.noChanges(existingEvent);
        }

        return CalendarEventMutationPlan.forExistingEvent(
                existingEvent,
                title,
                normalizedTitle,
                eventStart,
                eventEnd,
                coreDataChanged,
                resolvedClient,
                clientChanged,
                parsedTitle.paymentType(),
                paymentTypeChanged,
                matchedServices,
                serviceAssociationChanged
        );
    }

    private Client resolveClient(final Long userId,
                                 final User user,
                                 final EventTitleParser.ParsedTitle parsedTitle,
                                 final Map<String, Client> clientsByNormalizedName,
                                 final Map<String, String> normCache) {
        if (!parsedTitle.hasClient()) {
            return null;
        }

        final String normalizedClientName = normalizeWithCache(parsedTitle.clientName(), normCache);
        Client client = clientsByNormalizedName.get(normalizedClientName);
        if (client == null) {
            client = clientService.findOrCreateByName(userId, user, parsedTitle.clientName());
            clientsByNormalizedName.put(normalizedClientName, client);
        }
        return client;
    }

    private List<Service> resolveMatchedServices(final EventTitleParser.ParsedTitle parsedTitle,
                                                 final Map<String, Service> servicesByNormalizedDescription,
                                                 final Map<String, String> normCache) {
        if (parsedTitle.serviceNames().isEmpty()) {
            return List.of();
        }

        final List<Service> matchedServices = new ArrayList<>(parsedTitle.serviceNames().size());
        for (final String serviceName : parsedTitle.serviceNames()) {
            final String normalizedServiceName = normalizeWithCache(serviceName, normCache);
            final Service service = servicesByNormalizedDescription.get(normalizedServiceName);
            if (service != null) {
                matchedServices.add(service);
            }
        }
        return matchedServices;
    }

    private Instant extractInstant(final EventDateTime eventDateTime) {
        if (eventDateTime == null) {
            return Instant.now();
        }
        if (eventDateTime.getDateTime() != null) {
            return Instant.ofEpochMilli(eventDateTime.getDateTime().getValue());
        }
        if (eventDateTime.getDate() != null) {
            return Instant.ofEpochMilli(eventDateTime.getDate().getValue());
        }
        return Instant.now();
    }

    private String normalizeWithCache(final String rawValue, final Map<String, String> normalizationCache) {
        if (rawValue == null) {
            return normalizer.normalize(null);
        }
        return normalizationCache.computeIfAbsent(rawValue, normalizer::normalize);
    }

    private boolean isDeletedEvent(final Event event) {
        return "cancelled".equals(event.getStatus());
    }

    private boolean isUsableGoogleEvent(final Event googleEvent) {
        return googleEvent != null && googleEvent.getId() != null && !googleEvent.getId().isBlank();
    }

    private <K, V> Map<K, V> copyMap(final Map<K, V> source) {
        if (source == null || source.isEmpty()) {
            return new HashMap<>();
        }
        return new HashMap<>(source);
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
