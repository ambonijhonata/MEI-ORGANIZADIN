package com.api.calendar;

import com.api.client.Client;
import com.api.servicecatalog.Service;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@SuppressWarnings({
        "PMD.AtLeastOneConstructor",
        "PMD.LongVariable",
        "PMD.OnlyOneReturn",
        "PMD.SimplifyBooleanReturns"
})
public class CalendarSyncAssociationEvaluator {

    public boolean hasCoreDataChanges(final CalendarEvent existingEvent,
                                      final String title,
                                      final String normalizedTitle,
                                      final Instant eventStart,
                                      final Instant eventEnd) {
        return !existingEvent.matchesCoreData(title, normalizedTitle, eventStart, eventEnd);
    }

    public boolean hasServiceAssociationChanges(final CalendarEvent existingEvent,
                                                final List<Service> matchedServices,
                                                final Map<String, Integer> existingServiceIdentities) {
        final Map<String, Integer> persistedServiceIdentities =
                resolvePersistedServiceIdentityCounts(existingEvent, existingServiceIdentities);

        if (matchedServices.isEmpty()) {
            return hasPersistedAssociation(existingEvent, persistedServiceIdentities);
        }

        if (!existingEvent.isIdentified()) {
            return true;
        }

        final Service firstMatchedService = matchedServices.get(0);
        if (!existingEvent.hasServiceSnapshot(firstMatchedService.getDescription(), sumValues(matchedServices))) {
            return true;
        }

        return !persistedServiceIdentities.equals(serviceIdentityCounts(matchedServices));
    }

    public boolean isEquivalentClient(final Client existingClient, final Client resolvedClient) {
        if (existingClient == null || resolvedClient == null) {
            return existingClient == null && resolvedClient == null;
        }
        if (existingClient.getId() != null && resolvedClient.getId() != null) {
            return Objects.equals(existingClient.getId(), resolvedClient.getId());
        }
        return Objects.equals(existingClient.getNormalizedName(), resolvedClient.getNormalizedName())
                && Objects.equals(existingClient.getName(), resolvedClient.getName());
    }

    public String serviceIdentity(final Service service) {
        if (service == null) {
            return "none";
        }
        return serviceIdentity(
                service.getId(),
                service.getNormalizedDescription(),
                service.getDescription(),
                service.getValue()
        );
    }

    public String serviceIdentity(final Long serviceId,
                                  final String serviceNormalizedDescription,
                                  final String serviceDescription,
                                  final BigDecimal serviceValue) {
        if (serviceId != null) {
            return "id:" + serviceId;
        }
        if (serviceNormalizedDescription != null && !serviceNormalizedDescription.isBlank()) {
            return "normalized:" + serviceNormalizedDescription;
        }
        if (serviceDescription != null && !serviceDescription.isBlank()) {
            return "description:" + serviceDescription;
        }
        if (serviceValue != null) {
            return "value:" + serviceValue.stripTrailingZeros().toPlainString();
        }
        return "none";
    }

    public Map<String, Integer> serviceIdentityCounts(final List<Service> services) {
        final Map<String, Integer> identities = new HashMap<>();
        for (final Service service : services) {
            final String identity = serviceIdentity(service);
            identities.put(identity, identities.getOrDefault(identity, 0) + 1);
        }
        return identities;
    }

    public Map<String, Integer> resolvePersistedServiceIdentityCounts(final CalendarEvent existingEvent,
                                                                      final Map<String, Integer> existingServiceIdentities) {
        if (existingServiceIdentities != null && !existingServiceIdentities.isEmpty()) {
            return existingServiceIdentities;
        }

        final Map<String, Integer> fallbackCounts = new HashMap<>();
        if (existingEvent == null) {
            return fallbackCounts;
        }

        for (final CalendarEventServiceLink serviceLink : existingEvent.getServiceLinks()) {
            final String identity = serviceIdentity(serviceLink.getService());
            fallbackCounts.put(identity, fallbackCounts.getOrDefault(identity, 0) + 1);
        }

        if (!fallbackCounts.isEmpty()) {
            return fallbackCounts;
        }

        if (existingEvent.hasAnyServiceAssociationData()) {
            final String identity = serviceIdentity(
                    existingEvent.getPrimaryServiceId(),
                    existingEvent.getPrimaryServiceNormalizedDescription(),
                    existingEvent.getServiceDescriptionSnapshot(),
                    existingEvent.getServiceValueSnapshot()
            );
            fallbackCounts.put(identity, fallbackCounts.getOrDefault(identity, 0) + 1);
        }
        return fallbackCounts;
    }

    private boolean hasPersistedAssociation(final CalendarEvent existingEvent,
                                            final Map<String, Integer> existingServiceIdentities) {
        final Map<String, Integer> persistedServiceIdentities =
                resolvePersistedServiceIdentityCounts(existingEvent, existingServiceIdentities);
        return existingEvent.isIdentified()
                || !persistedServiceIdentities.isEmpty()
                || existingEvent.hasAnyServiceAssociationData();
    }

    private BigDecimal sumValues(final List<Service> services) {
        BigDecimal total = BigDecimal.ZERO;
        for (final Service service : services) {
            if (service != null && service.getValue() != null) {
                total = total.add(service.getValue());
            }
        }
        return total;
    }
}
