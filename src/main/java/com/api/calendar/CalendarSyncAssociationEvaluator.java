package com.api.calendar;

import com.api.client.Client;
import com.api.servicecatalog.Service;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@SuppressWarnings("PMD.LongVariable")
public class CalendarSyncAssociationEvaluator {
    private final CalendarServiceIdentityResolver identityResolver;

    public CalendarSyncAssociationEvaluator() {
        this(new CalendarServiceIdentityResolver());
    }

    /* package */ CalendarSyncAssociationEvaluator(final CalendarServiceIdentityResolver identityResolver) {
        this.identityResolver = identityResolver;
    }

    public boolean hasCoreDataChanges(final CalendarEvent existingEvent,
                                      final String title,
                                      final String normalizedTitle,
                                      final Instant eventStart,
                                      final Instant eventEnd) {
        return !existingEvent.matchesCoreData(title, normalizedTitle, eventStart, eventEnd);
    }

    public boolean hasServiceAssociationChanges(final CalendarEvent existingEvent,
                                                final List<Service> matchedServices,
                                                final Map<String, Integer> existingCounts) {
        final Map<String, Integer> savedCounts = resolvePersistedServiceIdentityCounts(existingEvent, existingCounts);
        final boolean changed;
        if (matchedServices.isEmpty()) {
            changed = hasPersistedAssociation(existingEvent, savedCounts);
        } else {
            final boolean missingIdentification = !existingEvent.isIdentified();
            final Service leadService = matchedServices.get(0);
            final boolean snapshotChanged = !CalendarEventLegacySupport.hasServiceSnapshot(
                    existingEvent.getServiceDescriptionSnapshot(),
                    existingEvent.getServiceValueSnapshot(),
                    leadService.getDescription(),
                    sumValues(matchedServices)
            );
            final boolean countChanged = !savedCounts.equals(identityResolver.countServices(matchedServices));
            changed = missingIdentification || snapshotChanged || countChanged;
        }
        return changed;
    }

    public boolean isEquivalentClient(final Client existingClient, final Client resolvedClient) {
        final boolean equivalent;
        if (existingClient == null || resolvedClient == null) {
            equivalent = existingClient == null && resolvedClient == null;
        } else {
            final boolean bothPersisted = existingClient.getId() != null && resolvedClient.getId() != null;
            equivalent = bothPersisted
                    ? Objects.equals(existingClient.getId(), resolvedClient.getId())
                    : Objects.equals(existingClient.getNormalizedName(), resolvedClient.getNormalizedName())
                    && Objects.equals(existingClient.getName(), resolvedClient.getName());
        }
        return equivalent;
    }

    public String serviceIdentity(final Service service) {
        return identityResolver.forService(service);
    }

    public String serviceIdentity(final Long serviceId,
                                  final String normDesc,
                                  final String desc,
                                  final BigDecimal amount) {
        return identityResolver.forSnapshot(serviceId, normDesc, desc, amount);
    }

    public Map<String, Integer> resolvePersistedServiceIdentityCounts(final CalendarEvent existingEvent,
                                                                      final Map<String, Integer> existingCounts) {
        return identityResolver.persistedCounts(existingEvent, existingCounts);
    }

    private boolean hasPersistedAssociation(final CalendarEvent existingEvent,
                                            final Map<String, Integer> existingCounts) {
        final Map<String, Integer> savedCounts = resolvePersistedServiceIdentityCounts(existingEvent, existingCounts);
        return existingEvent.isIdentified()
                || !savedCounts.isEmpty()
                || CalendarEventLegacySupport.hasAnyServiceAssociationData(existingEvent.getSnapshotOrEmpty());
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
