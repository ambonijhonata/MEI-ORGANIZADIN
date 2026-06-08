package com.api.calendar;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface CalendarEventRevenueRepository {
    String SUM = "SELECT COALESCE(SUM(e.snapshot.totalValue), 0) FROM CalendarEvent e ";
    String UID = "userId";
    String START_DATE = "startDate";
    String END_DATE = "endDate";
    String IDF = "WHERE e.user.id = :" + UID + " AND e.identified = true ";
    String PRD = "AND e.timing.start >= :" + START_DATE + " AND e.timing.start < :" + END_DATE + " ";
    String PAID = "AND (e.paymentType IS NOT NULL OR e.payments IS NOT EMPTY)";

    @Query(SUM + IDF + PRD)
    BigDecimal sumRevenueByUserAndPeriod(@Param(UID) Long userId,
                                         @Param(START_DATE) Instant startDate,
                                         @Param(END_DATE) Instant endDate);

    @Query(SUM + IDF + PRD + PAID)
    BigDecimal sumRevenueByUserAndPeriodPaidOnly(@Param(UID) Long userId,
                                                 @Param(START_DATE) Instant startDate,
                                                 @Param(END_DATE) Instant endDate);

    @Query("SELECT e FROM CalendarEvent e " +
            "WHERE e.user.id = :" + UID + " AND e.identified = true " +
            "AND e.timing.start >= :" + START_DATE + " AND e.timing.start < :" + END_DATE + " " +
            "ORDER BY e.timing.start ASC")
    List<CalendarEvent> findIdentifiedByUserAndPeriod(@Param("userId") Long userId,
                                                      @Param("startDate") Instant startDate,
                                                      @Param("endDate") Instant endDate);

    @Query("SELECT DISTINCT e FROM CalendarEvent e " +
            "LEFT JOIN FETCH e.serviceLinks serviceLink " +
            "WHERE e.user.id = :" + UID + " AND e.identified = true " +
            "AND e.timing.start >= :" + START_DATE + " AND e.timing.start < :" + END_DATE + " " +
            "ORDER BY e.timing.start ASC")
    List<CalendarEvent> findIdentifiedWithServiceLinksByUserAndPeriod(@Param("userId") Long userId,
                                                                       @Param("startDate") Instant startDate,
                                                                       @Param("endDate") Instant endDate);
}
