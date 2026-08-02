package com.api.calendar;

import com.api.servicecatalog.Service;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

final class CalendarEventLegacySupport {
    private CalendarEventLegacySupport() {
    }

    public static CalendarEventServiceSnapshot snapshotView(final CalendarEventServiceSnapshot snapshot) {
        return snapshot != null ? snapshot : CalendarEventServiceSnapshot.empty();
    }

    public static boolean hasServiceSnapshot(final String currentDesc,
                                      final BigDecimal currentTotal,
                                      final String description,
                                      final BigDecimal totalValue) {
        return java.util.Objects.equals(currentDesc, description)
                && ((currentTotal == null && totalValue == null)
                || (currentTotal != null
                && totalValue != null
                && currentTotal.compareTo(totalValue) == 0));
    }

    public static boolean hasAnyServiceAssociationData(final CalendarEventServiceSnapshot snapshot) {
        return snapshot.getDescription() != null || snapshot.getTotalValue() != null;
    }

    public static CalendarEventServiceState currentState(final Service primaryService,
                                                         final CalendarEventServiceSnapshot snapshot,
                                                         final List<CalendarEventServiceLink> serviceLinks,
                                                         final boolean identified) {
        return new CalendarEventServiceState(
                primaryService,
                snapshot,
                List.copyOf(serviceLinks),
                identified,
                false
        );
    }

    public static Optional<CalendarEventServiceLink> materializedPrimaryServiceLink(final CalendarEvent event,
                                                                                    final Service primaryService,
                                                                                    final List<CalendarEventServiceLink> serviceLinks,
                                                                                    final CalendarEventServiceSnapshot snapshot) {
        final Optional<CalendarEventServiceLink> link;
        if (!serviceLinks.isEmpty() || primaryService == null) {
            link = Optional.empty();
        } else {
            final CalendarEventServiceSeedReader.Seed seed = CalendarEventServiceSeedReader.read(primaryService);
            final String description = snapshot.descriptionOr(seed.description());
            final BigDecimal totalValue = snapshot.totalValueOr(seed.value());
            link = Optional.of(CalendarEventServiceLink.materialize(event, primaryService, 0, description, totalValue));
        }
        return link;
    }
}
