package com.api.calendar;

import java.util.List;

final class CalendarEventAssociationSupport {
    private CalendarEventAssociationSupport() {
    }

    public static CalendarEventServiceState currentState(final CalendarEvent owner) {
        return CalendarEventLegacySupport.currentState(
                owner.getService(),
                owner.getSnapshotOrEmpty(),
                owner.getServiceLinks(),
                owner.isIdentified()
        );
    }

    public static boolean requiresInitialAssociation(final CalendarEvent owner) {
        final CalendarEventServiceState currentState = currentState(owner);
        return !currentState.identified()
                || (currentState.primaryService() == null && currentState.serviceLinks().isEmpty());
    }

    public static List<CalendarEventServiceLink> materializedLinks(final CalendarEvent owner) {
        final CalendarEventServiceState currentState = currentState(owner);
        final List<CalendarEventServiceLink> links = new java.util.ArrayList<>(currentState.serviceLinks());
        CalendarEventLegacySupport.materializedPrimaryServiceLink(
                owner,
                owner.getService(),
                owner.getServiceLinks(),
                owner.getSnapshotOrEmpty()
        ).ifPresent(links::add);
        return links;
    }
}
