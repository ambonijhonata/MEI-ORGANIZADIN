package com.api.calendar;

import com.api.servicecatalog.Service;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CalendarEventServiceOccurrences {
    private final Map<String, Integer> countsByKey;

    private CalendarEventServiceOccurrences(final Map<String, Integer> countsByKey) {
        this.countsByKey = countsByKey;
    }

    public static CalendarEventServiceOccurrences empty() {
        return new CalendarEventServiceOccurrences(new HashMap<>());
    }

    public static CalendarEventServiceOccurrences from(final List<CalendarEventServiceLink> links) {
        final Map<String, Integer> counts = new HashMap<>();
        for (final CalendarEventServiceLink link : links) {
            final String identity = link.serviceIdentity();
            if (identity != null) {
                counts.put(identity, counts.getOrDefault(identity, 0) + 1);
            }
        }
        return new CalendarEventServiceOccurrences(counts);
    }

    public static int countRequested(final List<Service> services, final String identity) {
        int count = 0;
        for (final Service service : services) {
            if (identity != null && identity.equals(identityFor(service))) {
                count++;
            }
        }
        return count;
    }

    public static String identityFor(final Service service) {
        return service != null ? service.identityKey() : null;
    }

    public static BigDecimal totalLinkedValue(final List<CalendarEventServiceLink> links) {
        BigDecimal total = BigDecimal.ZERO;
        for (final CalendarEventServiceLink link : links) {
            total = total.add(link.valueOrZero());
        }
        return total;
    }

    public int currentCount(final String identity) {
        return identity != null ? countsByKey.getOrDefault(identity, 0) : 0;
    }

    public int nextOccurrenceIndex(final Service service) {
        final String identity = identityFor(service);
        final int nextIndex = currentCount(identity);
        record(identity, nextIndex + 1);
        return nextIndex;
    }

    public void record(final String identity, final int count) {
        if (identity != null) {
            countsByKey.put(identity, count);
        }
    }
}
