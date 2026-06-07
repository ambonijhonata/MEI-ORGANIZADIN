package com.api.calendar;

import com.api.servicecatalog.Service;
import java.util.ArrayList;
import java.util.List;

final class CalendarEventServiceOps {
    private CalendarEventServiceOps() {
    }

    public static CalendarEventServiceState associate(final CalendarEvent owner, final List<Service> services) {
        CalendarEventServiceState state = CalendarEventServiceState.empty();
        if (services != null && !services.isEmpty()) {
            final List<CalendarEventServiceLink> links = new ArrayList<>();
            final CalendarEventServiceOccurrences occurrences = CalendarEventServiceOccurrences.empty();
            for (final Service service : services) {
                final int occurrenceIndex = occurrences.nextOccurrenceIndex(service);
                links.add(new CalendarEventServiceLink(owner, service, occurrenceIndex));
            }

            state = new CalendarEventServiceState(
                    services.get(0),
                    CalendarEventServiceSnapshot.create(
                            services.get(0).getDescription(),
                            CalendarEventServiceOccurrences.totalLinkedValue(links)
                    ),
                    links,
                    true,
                    true
            );
        }
        return state;
    }

    public static CalendarEventServiceState enrich(final CalendarEvent owner, final List<Service> services) {
        CalendarEventServiceState state = CalendarEventAssociationSupport.currentState(owner);
        if (services != null && !services.isEmpty()) {
            if (CalendarEventAssociationSupport.requiresInitialAssociation(owner)) {
                state = associate(owner, services);
            } else {
                state = mergeServices(owner, services);
            }
        }
        return state;
    }

    private static CalendarEventServiceState mergeServices(final CalendarEvent owner, final List<Service> services) {
        final CalendarEventServiceState currentState = CalendarEventAssociationSupport.currentState(owner);
        final List<CalendarEventServiceLink> links = CalendarEventAssociationSupport.materializedLinks(owner);
        final CalendarEventServiceOccurrences occurrences = CalendarEventServiceOccurrences.from(links);
        boolean changed = false;
        for (final Service service : services) {
            final String identity = CalendarEventServiceOccurrences.identityFor(service);
            if (identity == null) {
                continue;
            }

            int existingCount = occurrences.currentCount(identity);
            final int requestedCount = CalendarEventServiceOccurrences.countRequested(services, identity);
            while (existingCount < requestedCount) {
                links.add(new CalendarEventServiceLink(owner, service, existingCount));
                changed = true;
                existingCount++;
            }
            occurrences.record(identity, existingCount);
        }

        final Service primaryService = currentState.primaryService() != null ? currentState.primaryService() : services.get(0);
        final CalendarEventServiceSnapshot currentSnapshot = currentState.snapshot();
        final String leadDescription = currentSnapshot.hasDescription()
                ? currentSnapshot.getDescription()
                : primaryService.getDescription();
        final CalendarEventServiceSnapshot snapshot = CalendarEventServiceSnapshot.create(
                leadDescription,
                CalendarEventServiceOccurrences.totalLinkedValue(links)
        );

        return new CalendarEventServiceState(primaryService, snapshot, links, true, changed);
    }
}
