package com.api.calendar;

import java.util.Collection;

@FunctionalInterface
public interface CalendarEventServiceLinkBulkRepository {

    void deleteInBulkByCalendarEventIdIn(Collection<Long> calendarEventIds);
}
