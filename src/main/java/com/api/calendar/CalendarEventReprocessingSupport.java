package com.api.calendar;

import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class CalendarEventReprocessingSupport {
    private final EventTitleParser parser;
    private final ServiceDescriptionNormalizer norm;

    /* package */ CalendarEventReprocessingSupport(final EventTitleParser parser,
                                                   final ServiceDescriptionNormalizer norm) {
        this.parser = parser;
        this.norm = norm;
    }

    /* package */ EventTitleParser.ParsedTitle parseTitle(final CalendarEvent event) {
        return parser.parse(event.getTitle());
    }

    /* package */ boolean applyParsedDetails(final CalendarEvent event,
                                             final EventTitleParser.ParsedTitle parsed,
                                             final Map<String, Service> servicesByName,
                                             final boolean replaceServices) {
        final List<Service> matchedServices = resolveMatchedServices(parsed, servicesByName);
        boolean changed = false;

        if (!matchedServices.isEmpty()) {
            changed = updateServices(event, matchedServices, replaceServices);
        }
        if (!event.hasPaymentType(parsed.paymentType())) {
            event.setPaymentType(parsed.paymentType());
            changed = true;
        }
        return changed;
    }

    private List<Service> resolveMatchedServices(final EventTitleParser.ParsedTitle parsed,
                                                 final Map<String, Service> servicesByName) {
        List<Service> matchedServices = List.of();
        if (!parsed.serviceNames().isEmpty()) {
            matchedServices = new ArrayList<>(parsed.serviceNames().size());
            for (final String serviceName : parsed.serviceNames()) {
                final String serviceKey = norm.normalize(serviceName);
                final Service service = servicesByName.get(serviceKey);
                if (service != null) {
                    matchedServices.add(service);
                }
            }
        }
        return matchedServices;
    }

    private boolean updateServices(final CalendarEvent event,
                                   final List<Service> matchedServices,
                                   final boolean replaceServices) {
        boolean changed = replaceServices;
        if (replaceServices) {
            event.associateServices(matchedServices);
        } else {
            changed = event.enrichServices(matchedServices);
        }
        return changed;
    }
}
