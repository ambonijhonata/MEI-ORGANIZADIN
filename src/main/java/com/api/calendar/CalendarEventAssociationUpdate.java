package com.api.calendar;

import com.api.client.Client;
import com.api.servicecatalog.Service;
import java.util.List;

public record CalendarEventAssociationUpdate(
        Client client,
        boolean clientChanged,
        PaymentType paymentType,
        boolean paymentChanged,
        List<Service> services,
        boolean servicesChanged,
        boolean replaceLinks
) {
    public CalendarEventAssociationUpdate {
        services = services == null ? List.of() : List.copyOf(services);
    }

    public static CalendarEventAssociationUpdate noChanges(final PaymentType paymentType) {
        return new CalendarEventAssociationUpdate(null, false, paymentType, false, List.of(), false, false);
    }

    public static CalendarEventAssociationUpdate forNew(final Client client,
                                                        final boolean hasClient,
                                                        final PaymentType paymentType,
                                                        final List<Service> services) {
        final List<Service> resolvedServices = services == null ? List.of() : List.copyOf(services);
        return new CalendarEventAssociationUpdate(
                client,
                hasClient,
                paymentType,
                paymentType != null,
                resolvedServices,
                !resolvedServices.isEmpty(),
                false
        );
    }

    public static CalendarEventAssociationUpdate forExisting(final Client client,
                                                             final boolean clientChanged,
                                                             final PaymentType paymentType,
                                                             final boolean paymentChanged,
                                                             final List<Service> services,
                                                             final boolean servicesChanged,
                                                             final boolean persistedEvent) {
        final List<Service> resolvedServices = services == null ? List.of() : List.copyOf(services);
        return new CalendarEventAssociationUpdate(
                client,
                clientChanged,
                paymentType,
                paymentChanged,
                resolvedServices,
                servicesChanged,
                servicesChanged && persistedEvent
        );
    }

    public boolean hasChanges() {
        return clientChanged || paymentChanged || servicesChanged;
    }
}
