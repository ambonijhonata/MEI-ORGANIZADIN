package com.api.calendar;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CalendarSyncServiceIdentityLoader {

    private final CalendarEventRepository eventRepo;
    private final CalendarEventServiceLinkRepository linkRepo;
    private final CalendarSyncAssociationEvaluator assocEval;

    /* package */ CalendarSyncServiceIdentityLoader(final CalendarEventRepository eventRepo,
                                                    final CalendarEventServiceLinkRepository linkRepo,
                                                    final CalendarSyncAssociationEvaluator assocEval) {
        this.eventRepo = eventRepo;
        this.linkRepo = linkRepo;
        this.assocEval = assocEval;
    }

    /* package */ Map<Long, Map<String, Integer>> loadByEventId(final Iterable<CalendarEvent> events) {
        final Map<Long, Map<String, Integer>> countsByEventId = new HashMap<>();
        final List<Long> eventIds = extractEventIds(events);
        if (shouldLoad(eventIds)) {
            appendLinkedRows(eventIds, countsByEventId);
            appendLegacyRows(eventIds, countsByEventId);
            ensureEmptyCounts(eventIds, countsByEventId);
        }
        return countsByEventId;
    }

    private boolean shouldLoad(final List<Long> eventIds) {
        return linkRepo != null && !eventIds.isEmpty();
    }

    private List<Long> extractEventIds(final Iterable<CalendarEvent> events) {
        final List<Long> eventIds = new ArrayList<>();
        if (events != null) {
            for (final CalendarEvent event : events) {
                if (event != null && event.getId() != null) {
                    eventIds.add(event.getId());
                }
            }
        }
        return eventIds;
    }

    private void appendLinkedRows(final List<Long> eventIds, final Map<Long, Map<String, Integer>> countsByEventId) {
        final List<CalendarEventServiceLinkRepository.ServiceIdentityRow> rows =
                linkRepo.findServiceIdentityRowsByCalendarEventIdIn(eventIds);
        if (rows != null) {
            for (final CalendarEventServiceLinkRepository.ServiceIdentityRow row : rows) {
                appendIdentity(
                        countsByEventId,
                        row.getCalendarEventId(),
                        row.getServiceId(),
                        row.getServiceNormalizedDescription(),
                        row.getServiceDescription(),
                        row.getServiceValue()
                );
            }
        }
    }

    private void appendLegacyRows(final List<Long> eventIds, final Map<Long, Map<String, Integer>> countsByEventId) {
        final List<CalendarEventRepository.ServiceIdentityRow> rows =
                eventRepo.findLegacyServiceIdentityRowsByCalendarEventIdIn(eventIds);
        if (rows != null) {
            for (final CalendarEventRepository.ServiceIdentityRow row : rows) {
                appendIdentity(
                        countsByEventId,
                        row.getCalendarEventId(),
                        row.getServiceId(),
                        row.getServiceNormalizedDescription(),
                        row.getServiceDescription(),
                        row.getServiceValue()
                );
            }
        }
    }

    private void appendIdentity(final Map<Long, Map<String, Integer>> countsByEventId,
                                final Long eventId,
                                final Long serviceId,
                                final String normDesc,
                                final String desc,
                                final BigDecimal amount) {
        if (eventId != null) {
            final Map<String, Integer> counts = countsForEvent(countsByEventId, eventId);
            final String identity = assocEval.serviceIdentity(serviceId, normDesc, desc, amount);
            counts.put(identity, counts.getOrDefault(identity, 0) + 1);
        }
    }

    private Map<String, Integer> countsForEvent(final Map<Long, Map<String, Integer>> countsByEventId,
                                                final Long eventId) {
        Map<String, Integer> counts = countsByEventId.get(eventId);
        if (counts == null) {
            counts = new HashMap<>();
            countsByEventId.put(eventId, counts);
        }
        return counts;
    }

    private void ensureEmptyCounts(final List<Long> eventIds, final Map<Long, Map<String, Integer>> countsByEventId) {
        for (final Long eventId : eventIds) {
            countsForEvent(countsByEventId, eventId);
        }
    }
}
