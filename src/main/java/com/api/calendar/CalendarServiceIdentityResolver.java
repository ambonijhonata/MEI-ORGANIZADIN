package com.api.calendar;

import com.api.servicecatalog.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CalendarServiceIdentityResolver {
    public CalendarServiceIdentityResolver() {
    }

    public String forService(final Service service) {
        final String identity = service != null ? service.identityKey() : null;
        return identity != null ? identity : "none";
    }

    public String forSnapshot(final Long serviceId,
                              final String normDesc,
                              final String desc,
                              final java.math.BigDecimal amount) {
        String identity = "none";
        if (serviceId != null) {
            identity = "id:" + serviceId;
        } else if (normDesc != null && !normDesc.isBlank()) {
            identity = "normalized:" + normDesc;
        } else if (desc != null && !desc.isBlank()) {
            identity = "description:" + desc;
        } else if (amount != null) {
            identity = "value:" + amount.stripTrailingZeros().toPlainString();
        }
        return identity;
    }

    public Map<String, Integer> countServices(final List<Service> services) {
        final Map<String, Integer> identities = new HashMap<>();
        for (final Service service : services) {
            final String identity = forService(service);
            identities.put(identity, identities.getOrDefault(identity, 0) + 1);
        }
        return identities;
    }

    public Map<String, Integer> persistedCounts(final CalendarEvent existingEvent,
                                                final Map<String, Integer> existingCounts) {
        Map<String, Integer> resolvedCounts = existingCounts;
        if (resolvedCounts == null || resolvedCounts.isEmpty()) {
            resolvedCounts = fallbackCounts(existingEvent);
        }
        return resolvedCounts;
    }

    private Map<String, Integer> fallbackCounts(final CalendarEvent existingEvent) {
        final Map<String, Integer> fallbackCounts = new HashMap<>();
        if (existingEvent != null) {
            appendLinkedCounts(existingEvent, fallbackCounts);
            if (fallbackCounts.isEmpty()) {
                appendLegacyCounts(existingEvent, fallbackCounts);
            }
        }
        return fallbackCounts;
    }

    private void appendLinkedCounts(final CalendarEvent existingEvent,
                                    final Map<String, Integer> fallbackCounts) {
        for (final CalendarEventServiceLink serviceLink : existingEvent.getServiceLinks()) {
            final String identity = forService(serviceLink.getService());
            fallbackCounts.put(identity, fallbackCounts.getOrDefault(identity, 0) + 1);
        }
    }

    private void appendLegacyCounts(final CalendarEvent existingEvent,
                                    final Map<String, Integer> fallbackCounts) {
        if (existingEvent.hasAnyServiceAssociationData()) {
            final String identity = forSnapshot(
                    existingEvent.getPrimaryServiceId(),
                    existingEvent.getPrimaryServiceNormalizedDescription(),
                    existingEvent.getServiceDescriptionSnapshot(),
                    existingEvent.getServiceValueSnapshot()
            );
            fallbackCounts.put(identity, fallbackCounts.getOrDefault(identity, 0) + 1);
        }
    }
}
