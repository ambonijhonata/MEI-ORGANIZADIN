package com.api.calendar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.math.BigDecimal;
import java.util.List;

public interface CalendarEventPaymentRepository extends JpaRepository<CalendarEventPayment, Long> {
    List<CalendarEventPayment> findByCalendarEventIdOrderByIdAsc(Long calendarEventId);
    void deleteByCalendarEventId(Long calendarEventId);

    @Query("SELECT new com.api.calendar.CalendarEventPaymentTotal(" +
            "p.calendarEvent.id, COALESCE(SUM(p.amount), 0)) " +
            "FROM CalendarEventPayment p " +
            "WHERE p.calendarEvent.id IN :eventIds " +
            "GROUP BY p.calendarEvent.id")
    List<CalendarEventPaymentTotal> summarizePaidAmountsByEventIdIn(@Param("eventIds") Collection<Long> eventIds);
}
