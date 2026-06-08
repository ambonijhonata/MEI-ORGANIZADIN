package com.api.calendar;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long>,
        CalendarEventLookupRepository,
        CalendarEventGoogleRepository,
        CalendarEventPeriodRepository,
        CalendarEventRevenueRepository,
        CalendarEventAssociationRepository {

    interface ServiceIdentityRow {
        Long getCalendarEventId();
        Long getServiceId();
        String getServiceNormalizedDescription();
        String getServiceDescription();
        java.math.BigDecimal getServiceValue();
    }
}
