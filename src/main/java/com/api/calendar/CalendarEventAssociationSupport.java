package com.api.calendar;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("PMD.LawOfDemeter")
final class CalendarEventAssociationSupport {
    private CalendarEventAssociationSupport() {
    }

    public static CalendarEventServiceState currentState(final CalendarEvent owner) {
        return new CalendarEventServiceState(
                owner.getService(),
                owner.getSnapshot(),
                List.copyOf(owner.getServiceLinks()),
                owner.isIdentified(),
                false
        );
    }

    public static boolean requiresInitialAssociation(final CalendarEvent owner) {
        return !owner.isIdentified() || (owner.getService() == null && owner.getServiceLinks().isEmpty());
    }

    public static List<CalendarEventServiceLink> materializedLinks(final CalendarEvent owner) {
        final List<CalendarEventServiceLink> links = new ArrayList<>(owner.getServiceLinks());
        if (links.isEmpty() && owner.getService() != null) {
            final CalendarEventServiceSnapshot snapshot = owner.getSnapshot();
            final String description = snapshot.hasDescription()
                    ? snapshot.getDescription()
                    : owner.getService().getDescription();
            final BigDecimal totalValue = snapshot.hasValue()
                    ? snapshot.getTotalValue()
                    : owner.getService().getValue();
            links.add(new CalendarEventServiceLink(owner, owner.getService(), 0, description, totalValue));
        }
        return links;
    }
}
