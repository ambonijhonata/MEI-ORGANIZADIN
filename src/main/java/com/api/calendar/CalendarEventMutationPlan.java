package com.api.calendar;

import com.api.client.Client;
import com.api.servicecatalog.Service;
import java.time.Instant;
import java.util.List;

public record CalendarEventMutationPlan(
        CalendarEvent event,
        boolean isNew,
        CalendarEventCoreUpdate core,
        CalendarEventAssociationUpdate association
) {
    public static CalendarEventMutationPlan noChanges(final CalendarEvent event) {
        return new CalendarEventMutationPlan(
                event,
                false,
                CalendarEventCoreUpdate.current(event),
                CalendarEventAssociationUpdate.noChanges(event.getPaymentType())
        );
    }

    public static CalendarEventMutationPlan forNewEvent(final CalendarEvent event,
                                                        final Client client,
                                                        final boolean hasClient,
                                                        final PaymentType paymentType,
                                                        final List<Service> services) {
        return new CalendarEventMutationPlan(
                event,
                true,
                CalendarEventCoreUpdate.current(event),
                CalendarEventAssociationUpdate.forNew(client, hasClient, paymentType, services)
        );
    }

    public static CalendarEventMutationPlan forExistingEvent(final CalendarEvent event,
                                                             final CalendarEventCoreUpdate core,
                                                             final CalendarEventAssociationUpdate association) {
        return new CalendarEventMutationPlan(
                event,
                false,
                core,
                association
        );
    }

    public boolean shouldPersist() {
        return isNew || core.changed() || association.hasChanges();
    }

    public boolean shouldReplaceServiceLinks() {
        return association.replaceLinks();
    }

    public String title() {
        return core.title();
    }

    public String normalizedTitle() {
        return core.normalizedTitle();
    }

    public Instant eventStart() {
        return core.start();
    }

    public Instant eventEnd() {
        return core.end();
    }

    public boolean coreDataChanged() {
        return core.changed();
    }

    public Client resolvedClient() {
        return association.client();
    }

    public boolean clientChanged() {
        return association.clientChanged();
    }

    public PaymentType paymentType() {
        return association.paymentType();
    }

    public boolean paymentTypeChanged() {
        return association.paymentChanged();
    }

    public List<Service> matchedServices() {
        return association.services();
    }

    public boolean serviceAssociationChanged() {
        return association.servicesChanged();
    }

    public CalendarEvent calendarEvent() {
        return event;
    }
}
