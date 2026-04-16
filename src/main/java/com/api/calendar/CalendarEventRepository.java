package com.api.calendar;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    Optional<CalendarEvent> findByUserIdAndGoogleEventId(Long userId, String googleEventId);

    List<CalendarEvent> findByUserIdAndGoogleEventIdIn(Long userId, Collection<String> googleEventIds);

    @Query("SELECT DISTINCT e FROM CalendarEvent e " +
            "LEFT JOIN FETCH e.service legacyService " +
            "LEFT JOIN FETCH e.serviceLinks serviceLink " +
            "LEFT JOIN FETCH serviceLink.service linkedService " +
            "WHERE e.user.id = :userId")
    List<CalendarEvent> findAllWithAssociationsByUserId(@Param("userId") Long userId);

    @Query("SELECT DISTINCT e FROM CalendarEvent e " +
            "LEFT JOIN FETCH e.service legacyService " +
            "LEFT JOIN FETCH e.serviceLinks serviceLink " +
            "LEFT JOIN FETCH serviceLink.service linkedService " +
            "WHERE e.user.id = :userId AND e.googleEventId IN :googleEventIds")
    List<CalendarEvent> findWithAssociationsByUserIdAndGoogleEventIdIn(@Param("userId") Long userId,
                                                                        @Param("googleEventIds") Collection<String> googleEventIds);

    Page<CalendarEvent> findByUserId(Long userId, Pageable pageable);

    Page<CalendarEvent> findByUserIdAndEventStartGreaterThanEqualAndEventStartLessThan(
            Long userId, Instant eventStart, Instant eventEnd, Pageable pageable);

    Page<CalendarEvent> findByUserIdAndEventStartGreaterThanEqual(
            Long userId, Instant eventStart, Pageable pageable);

    Page<CalendarEvent> findByUserIdAndEventStartLessThan(
            Long userId, Instant eventEnd, Pageable pageable);

    List<CalendarEvent> findByUserIdAndIdentifiedFalse(Long userId);

    boolean existsByServiceId(Long serviceId);

    boolean existsByClientId(Long clientId);

    void deleteByUserIdAndGoogleEventId(Long userId, String googleEventId);

    @Query("SELECT COALESCE(SUM(e.serviceValueSnapshot), 0) FROM CalendarEvent e " +
            "WHERE e.user.id = :userId AND e.identified = true " +
            "AND e.eventStart >= :startDate AND e.eventStart < :endDate")
    BigDecimal sumRevenueByUserAndPeriod(@Param("userId") Long userId,
                                         @Param("startDate") Instant startDate,
                                         @Param("endDate") Instant endDate);

    @Query("SELECT COALESCE(SUM(e.serviceValueSnapshot), 0) FROM CalendarEvent e " +
            "WHERE e.user.id = :userId AND e.identified = true " +
            "AND e.eventStart >= :startDate AND e.eventStart < :endDate " +
            "AND (e.paymentType IS NOT NULL OR e.payments IS NOT EMPTY)")
    BigDecimal sumRevenueByUserAndPeriodPaidOnly(@Param("userId") Long userId,
                                                 @Param("startDate") Instant startDate,
                                                 @Param("endDate") Instant endDate);

    @Query("SELECT e FROM CalendarEvent e " +
            "WHERE e.user.id = :userId AND e.identified = true " +
            "AND e.eventStart >= :startDate AND e.eventStart < :endDate " +
            "ORDER BY e.eventStart ASC")
    List<CalendarEvent> findIdentifiedByUserAndPeriod(@Param("userId") Long userId,
                                                       @Param("startDate") Instant startDate,
                                                       @Param("endDate") Instant endDate);

    Optional<CalendarEvent> findByIdAndUserId(Long id, Long userId);
}
