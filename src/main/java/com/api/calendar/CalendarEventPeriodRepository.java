package com.api.calendar;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CalendarEventPeriodRepository {
    String SELECT_EVENT = "SELECT e FROM CalendarEvent e ";
    String WHERE_USER = "WHERE e.user.id = :userId ";
    String PERIOD_START = "AND e.timing.start >= :eventStart ";
    String PERIOD_END = "AND e.timing.start < :eventEnd";

    @Query(SELECT_EVENT + WHERE_USER + PERIOD_START + PERIOD_END)
    Page<CalendarEvent> findByUserIdAndEventStartGreaterThanEqualAndEventStartLessThan(
            @Param("userId") Long userId,
            @Param("eventStart") Instant eventStart,
            @Param("eventEnd") Instant eventEnd,
            Pageable pageable);

    @Query(SELECT_EVENT + WHERE_USER + PERIOD_START)
    Page<CalendarEvent> findByUserIdAndEventStartGreaterThanEqual(
            @Param("userId") Long userId,
            @Param("eventStart") Instant eventStart,
            Pageable pageable);

    @Query(SELECT_EVENT + WHERE_USER + PERIOD_END)
    Page<CalendarEvent> findByUserIdAndEventStartLessThan(
            @Param("userId") Long userId,
            @Param("eventEnd") Instant eventEnd,
            Pageable pageable);

    List<CalendarEvent> findByUserIdAndIdentifiedFalse(Long userId);
}
