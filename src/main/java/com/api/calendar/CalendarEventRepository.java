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
@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.ShortVariable", "PMD.TooManyMethods"})

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    interface ServiceIdentityRow {
        Long getCalendarEventId();
        Long getServiceId();
        String getServiceNormalizedDescription();
        String getServiceDescription();
        BigDecimal getServiceValue();
    }

    @Query("SELECT e FROM CalendarEvent e " +
            "WHERE e.user.id = :userId " +
            "AND e.source = com.api.calendar.CalendarEventSource.GOOGLE " +
            "AND e.googleEventId = :googleEventId")
    Optional<CalendarEvent> findByUserIdAndGoogleEventId(@Param("userId") Long userId,
                                                         @Param("googleEventId") String googleEventId);

    @Query("SELECT e FROM CalendarEvent e " +
            "WHERE e.user.id = :userId " +
            "AND e.source = com.api.calendar.CalendarEventSource.GOOGLE " +
            "AND e.googleEventId IN :googleEventIds")
    List<CalendarEvent> findByUserIdAndGoogleEventIdIn(@Param("userId") Long userId,
                                                       @Param("googleEventIds") Collection<String> googleEventIds);

    @Query("SELECT DISTINCT e FROM CalendarEvent e " +
            "LEFT JOIN FETCH e.service legacyService " +
            "LEFT JOIN FETCH e.serviceLinks serviceLink " +
            "LEFT JOIN FETCH serviceLink.service linkedService " +
            "WHERE e.user.id = :userId AND e.source = com.api.calendar.CalendarEventSource.GOOGLE")
    List<CalendarEvent> findAllWithAssociationsByUserId(@Param("userId") Long userId);

    @Query("SELECT e FROM CalendarEvent e " +
            "WHERE e.user.id = :userId " +
            "AND e.source = com.api.calendar.CalendarEventSource.GOOGLE " +
            "AND e.googleEventId IS NOT NULL " +
            "AND e.googleEventId <> ''")
    List<CalendarEvent> findGoogleBackedByUserId(@Param("userId") Long userId);

    @Query("SELECT e FROM CalendarEvent e " +
            "WHERE e.user.id = :userId " +
            "AND e.source = com.api.calendar.CalendarEventSource.GOOGLE " +
            "AND e.googleEventId IS NOT NULL " +
            "AND e.googleEventId <> '' " +
            "AND e.timing.start >= :startDate")
    List<CalendarEvent> findGoogleBackedByUserIdAndEventStartGreaterThanEqual(
            @Param("userId") Long userId,
            @Param("startDate") Instant startDate
    );

    @Query("SELECT DISTINCT e FROM CalendarEvent e " +
            "LEFT JOIN FETCH e.service legacyService " +
            "LEFT JOIN FETCH e.serviceLinks serviceLink " +
            "LEFT JOIN FETCH serviceLink.service linkedService " +
            "WHERE e.user.id = :userId " +
            "AND e.source = com.api.calendar.CalendarEventSource.GOOGLE " +
            "AND e.googleEventId IN :googleEventIds")
    List<CalendarEvent> findWithAssociationsByUserIdAndGoogleEventIdIn(@Param("userId") Long userId,
                                                                        @Param("googleEventIds") Collection<String> googleEventIds);

    Page<CalendarEvent> findByUserId(Long userId, Pageable pageable);

    @Query("SELECT e FROM CalendarEvent e WHERE e.user.id = :userId AND e.timing.start >= :eventStart AND e.timing.start < :eventEnd")
    Page<CalendarEvent> findByUserIdAndEventStartGreaterThanEqualAndEventStartLessThan(
            @Param("userId") Long userId,
            @Param("eventStart") Instant eventStart,
            @Param("eventEnd") Instant eventEnd,
            Pageable pageable);

    @Query("SELECT e FROM CalendarEvent e WHERE e.user.id = :userId AND e.timing.start >= :eventStart")
    Page<CalendarEvent> findByUserIdAndEventStartGreaterThanEqual(
            @Param("userId") Long userId,
            @Param("eventStart") Instant eventStart,
            Pageable pageable);

    @Query("SELECT e FROM CalendarEvent e WHERE e.user.id = :userId AND e.timing.start < :eventEnd")
    Page<CalendarEvent> findByUserIdAndEventStartLessThan(
            @Param("userId") Long userId,
            @Param("eventEnd") Instant eventEnd,
            Pageable pageable);

    List<CalendarEvent> findByUserIdAndIdentifiedFalse(Long userId);

    boolean existsByServiceId(Long serviceId);

    boolean existsByClientId(Long clientId);

    @Query("SELECT e.id AS calendarEventId, " +
            "s.id AS serviceId, " +
            "s.normalizedDescription AS serviceNormalizedDescription, " +
            "s.description AS serviceDescription, " +
            "s.value AS serviceValue " +
            "FROM CalendarEvent e " +
            "JOIN e.service s " +
            "WHERE e.id IN :calendarEventIds")
    List<ServiceIdentityRow> findLegacyServiceIdentityRowsByCalendarEventIdIn(
            @Param("calendarEventIds") Collection<Long> calendarEventIds
    );

    void deleteByUserIdAndGoogleEventId(Long userId, String googleEventId);

    @Query("SELECT COALESCE(SUM(e.snapshot.totalValue), 0) FROM CalendarEvent e " +
            "WHERE e.user.id = :userId AND e.identified = true " +
            "AND e.timing.start >= :startDate AND e.timing.start < :endDate")
    BigDecimal sumRevenueByUserAndPeriod(@Param("userId") Long userId,
                                         @Param("startDate") Instant startDate,
                                         @Param("endDate") Instant endDate);

    @Query("SELECT COALESCE(SUM(e.snapshot.totalValue), 0) FROM CalendarEvent e " +
            "WHERE e.user.id = :userId AND e.identified = true " +
            "AND e.timing.start >= :startDate AND e.timing.start < :endDate " +
            "AND (e.paymentType IS NOT NULL OR e.payments IS NOT EMPTY)")
    BigDecimal sumRevenueByUserAndPeriodPaidOnly(@Param("userId") Long userId,
                                                 @Param("startDate") Instant startDate,
                                                 @Param("endDate") Instant endDate);

    @Query("SELECT e FROM CalendarEvent e " +
            "WHERE e.user.id = :userId AND e.identified = true " +
            "AND e.timing.start >= :startDate AND e.timing.start < :endDate " +
            "ORDER BY e.timing.start ASC")
    List<CalendarEvent> findIdentifiedByUserAndPeriod(@Param("userId") Long userId,
                                                       @Param("startDate") Instant startDate,
                                                       @Param("endDate") Instant endDate);

    @Query("SELECT DISTINCT e FROM CalendarEvent e " +
            "LEFT JOIN FETCH e.serviceLinks serviceLink " +
            "WHERE e.user.id = :userId AND e.identified = true " +
            "AND e.timing.start >= :startDate AND e.timing.start < :endDate " +
            "ORDER BY e.timing.start ASC")
    List<CalendarEvent> findIdentifiedWithServiceLinksByUserAndPeriod(@Param("userId") Long userId,
                                                                       @Param("startDate") Instant startDate,
                                                                       @Param("endDate") Instant endDate);

    Optional<CalendarEvent> findByIdAndUserId(Long id, Long userId);
}
