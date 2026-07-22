package com.api.calendar;

import java.util.List;

final class CalendarEventAssociationSupport {
    private CalendarEventAssociationSupport() {
    }

    public static CalendarEventServiceState currentState(final CalendarEvent owner) {
        return owner.currentServiceState();
    }

    public static boolean requiresInitialAssociation(final CalendarEvent owner) {
        final CalendarEventServiceState currentState = owner.currentServiceState();
        return !currentState.identified()
                || (currentState.primaryService() == null && currentState.serviceLinks().isEmpty());
    }

    public static List<CalendarEventServiceLink> materializedLinks(final CalendarEvent owner) {
        final CalendarEventServiceState currentState = owner.currentServiceState();
        final List<CalendarEventServiceLink> links = new java.util.ArrayList<>(currentState.serviceLinks());
        owner.materializedPrimaryServiceLink().ifPresent(links::add);
        return links;
    }
}
