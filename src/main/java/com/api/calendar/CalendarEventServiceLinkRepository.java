package com.api.calendar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface CalendarEventServiceLinkRepository extends JpaRepository<CalendarEventServiceLink, Long>,
        CalendarEventServiceLinkBulkRepository {

    List<CalendarEventServiceLink> findByCalendarEventId(Long calendarEventId);

    void deleteByCalendarEventId(Long calendarEventId);

    boolean existsByServiceId(Long serviceId);

    @Query("SELECT sl FROM CalendarEventServiceLink sl " +
            "JOIN FETCH sl.calendarEvent e " +
            "WHERE e.user.id = :userId AND e.identified = true " +
            "AND e.eventStart >= :startDate AND e.eventStart < :endDate " +
            "ORDER BY e.eventStart ASC")
    List<CalendarEventServiceLink> findByUserAndPeriod(@Param("userId") Long userId,
                                                        @Param("startDate") Instant startDate,
                                                        @Param("endDate") Instant endDate);

    @Query("SELECT sl FROM CalendarEventServiceLink sl " +
            "JOIN FETCH sl.calendarEvent e " +
            "WHERE e.user.id = :userId AND e.identified = true " +
            "AND e.eventStart >= :startDate AND e.eventStart < :endDate " +
            "AND (e.paymentType IS NOT NULL OR e.payments IS NOT EMPTY) " +
            "ORDER BY e.eventStart ASC")
    List<CalendarEventServiceLink> findByUserAndPeriodPaidOnly(@Param("userId") Long userId,
                                                                @Param("startDate") Instant startDate,
                                                                @Param("endDate") Instant endDate);

    @Query("SELECT sl.calendarEvent.id AS calendarEventId, " +
            "sl.service.id AS serviceId, " +
            "sl.service.normalizedDescription AS serviceNormalizedDescription, " +
            "sl.service.description AS serviceDescription, " +
            "sl.service.value AS serviceValue " +
            "FROM CalendarEventServiceLink sl " +
            "WHERE sl.calendarEvent.id IN :calendarEventIds")
    List<ServiceIdentityRow> findServiceIdentityRowsByCalendarEventIdIn(
            @Param("calendarEventIds") Collection<Long> calendarEventIds
    );

    interface ServiceIdentityRow {
        Long getCalendarEventId();
        Long getServiceId();
        String getServiceNormalizedDescription();
        String getServiceDescription();
        BigDecimal getServiceValue();
    }
}
