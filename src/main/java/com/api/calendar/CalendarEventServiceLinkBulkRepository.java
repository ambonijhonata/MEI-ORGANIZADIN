package com.api.calendar;

import java.util.Collection;

public interface CalendarEventServiceLinkBulkRepository {

    void deleteInBulkByCalendarEventIdIn(Collection<Long> calendarEventIds);
}
