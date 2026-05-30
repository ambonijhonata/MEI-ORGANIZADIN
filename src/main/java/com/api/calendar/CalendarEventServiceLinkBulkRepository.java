package com.api.calendar;

import java.util.Collection;
@SuppressWarnings("PMD.ImplicitFunctionalInterface")

public interface CalendarEventServiceLinkBulkRepository {

    void deleteInBulkByCalendarEventIdIn(Collection<Long> calendarEventIds);
}
