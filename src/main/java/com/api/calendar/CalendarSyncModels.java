package com.api.calendar;

import com.api.client.Client;
import com.api.google.GoogleCalendarSyncEvent;
import com.api.servicecatalog.Service;
import com.api.user.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"PMD.LongVariable", "PMD.AvoidDuplicateLiterals"})
record CalendarSyncLookups(
        Map<String, Client> clientsByNormalizedName,
        Map<String, Service> servicesByNormalizedDescription
) {}

record CalendarSyncChunkRequest(
        Long userId,
        User user,
        List<GoogleCalendarSyncEvent> googleEvents,
        CalendarSyncLookups lookups,
        boolean fullSync,
        boolean allowDeletes,
        Map<String, String> normCache
) {}

record CalendarSyncExecutionRequest(
        Long userId,
        User user,
        SyncState syncState,
        List<GoogleCalendarSyncEvent> googleEvents,
        boolean fullSync,
        boolean allowDeletes,
        String tokenBeforeSync,
        String nextSyncToken,
        String syncMode,
        LocalDate startDate,
        Map<String, String> normCache
) {}

@SuppressWarnings("PMD.LongVariable")
record CalendarScopeReconciliationResult(
        CalendarSyncMutations reconciledMutations,
        List<CalendarEvent> additionalDeletions
) {}

record CalendarSyncExecution(
        CalendarSyncService.SyncResult result,
        long dbLookupMs,
        long processingMs,
        long dbWriteMs
) {}

@SuppressWarnings({"PMD.LongVariable", "PMD.CommentDefaultAccessModifier"})
record CalendarSyncMutations(
        List<CalendarEventMutationPlan> upserts,
        List<CalendarEvent> deletions,
        Set<Long> serviceLinkReplacementEventIds,
        int created,
        int updated,
        int deleted
) {
    static CalendarSyncMutations empty() {
        return new CalendarSyncMutations(List.of(), List.of(), Set.of(), 0, 0, 0);
    }
}

@SuppressWarnings({"PMD.LongVariable", "PMD.ExcessiveParameterList", "PMD.CommentDefaultAccessModifier"})
record CalendarEventMutationPlan(
        CalendarEvent calendarEvent,
        boolean isNew,
        boolean shouldPersist,
        boolean shouldReplaceServiceLinks,
        String title,
        String normalizedTitle,
        Instant eventStart,
        Instant eventEnd,
        boolean coreDataChanged,
        Client resolvedClient,
        boolean clientChanged,
        PaymentType paymentType,
        boolean paymentTypeChanged,
        List<Service> matchedServices,
        boolean serviceAssociationChanged
) {
    static CalendarEventMutationPlan noChanges(final CalendarEvent calendarEvent) {
        return new CalendarEventMutationPlan(
                calendarEvent,
                false,
                false,
                false,
                calendarEvent.getTitle(),
                calendarEvent.getNormalizedTitle(),
                calendarEvent.getEventStart(),
                calendarEvent.getEventEnd(),
                false,
                null,
                false,
                calendarEvent.getPaymentType(),
                false,
                List.of(),
                false
        );
    }

    static CalendarEventMutationPlan forNewEvent(final CalendarEvent calendarEvent,
                                                 final Client resolvedClient,
                                                 final boolean hasClient,
                                                 final PaymentType paymentType,
                                                 final List<Service> matchedServices) {
        final List<Service> resolvedServices = matchedServices == null ? List.of() : List.copyOf(matchedServices);
        return new CalendarEventMutationPlan(
                calendarEvent,
                true,
                true,
                false,
                calendarEvent.getTitle(),
                calendarEvent.getNormalizedTitle(),
                calendarEvent.getEventStart(),
                calendarEvent.getEventEnd(),
                false,
                resolvedClient,
                hasClient,
                paymentType,
                paymentType != null,
                resolvedServices,
                !resolvedServices.isEmpty()
        );
    }

    static CalendarEventMutationPlan forExistingEvent(final CalendarEvent calendarEvent,
                                                      final String title,
                                                      final String normalizedTitle,
                                                      final Instant eventStart,
                                                      final Instant eventEnd,
                                                      final boolean coreDataChanged,
                                                      final Client resolvedClient,
                                                      final boolean clientChanged,
                                                      final PaymentType paymentType,
                                                      final boolean paymentTypeChanged,
                                                      final List<Service> matchedServices,
                                                      final boolean serviceAssociationChanged) {
        return new CalendarEventMutationPlan(
                calendarEvent,
                false,
                true,
                serviceAssociationChanged && calendarEvent.getId() != null,
                title,
                normalizedTitle,
                eventStart,
                eventEnd,
                coreDataChanged,
                resolvedClient,
                clientChanged,
                paymentType,
                paymentTypeChanged,
                matchedServices == null ? List.of() : List.copyOf(matchedServices),
                serviceAssociationChanged
        );
    }
}
