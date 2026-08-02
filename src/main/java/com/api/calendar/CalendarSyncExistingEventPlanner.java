package com.api.calendar;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

@Component
public class CalendarSyncExistingEventPlanner {

    private final CalendarSyncAssociationEvaluator assocEval;

    public CalendarSyncExistingEventPlanner(final CalendarSyncAssociationEvaluator assocEval) {
        this.assocEval = assocEval;
    }

    public CalendarEventMutationPlan plan(final CalendarEvent existingEvent,
                                          final String title,
                                          final java.time.Instant eventStart,
                                          final java.time.Instant eventEnd,
                                          final CalendarSyncResolvedEventDetails resolvedDetails,
                                          final Map<String, Integer> serviceIdentity) {
        final boolean coreDataChanged = assocEval.hasCoreDataChanges(
                existingEvent,
                title,
                resolvedDetails.normalizedTitle(),
                eventStart,
                eventEnd
        );
        final boolean clientChanged = resolvedDetails.parsedTitle().hasClient()
                && !assocEval.isEquivalentClient(
                existingEvent.getClient(),
                resolvedDetails.resolvedClient()
        );
        final boolean serviceChanged = assocEval.hasServiceAssociationChanges(
                existingEvent,
                resolvedDetails.matchedServices(),
                serviceIdentity
        );
        final boolean paymentChanged =
                !Objects.equals(existingEvent.getPaymentType(), resolvedDetails.parsedTitle().paymentType());
        final boolean shouldPersist =
                coreDataChanged || clientChanged || serviceChanged || paymentChanged;

        final CalendarEventMutationPlan mutationPlan;
        if (shouldPersist) {
            mutationPlan = CalendarEventMutationPlan.forExistingEvent(
                    existingEvent,
                    CalendarEventCoreUpdate.changed(
                            title,
                            resolvedDetails.normalizedTitle(),
                            eventStart,
                            eventEnd,
                            coreDataChanged
                    ),
                    CalendarEventAssociationUpdate.forExisting(
                            resolvedDetails.resolvedClient(),
                            clientChanged,
                            resolvedDetails.parsedTitle().paymentType(),
                            paymentChanged,
                            resolvedDetails.matchedServices(),
                            serviceChanged,
                            existingEvent.getId() != null
                    )
            );
        } else {
            mutationPlan = CalendarEventMutationPlan.noChanges(existingEvent);
        }
        return mutationPlan;
    }
}
