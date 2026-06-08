package com.api.calendar;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CalendarEventGoogleRepository {
    String SEL = "SELECT e FROM CalendarEvent e ";
    String SEL_DIST = "SELECT DISTINCT e FROM CalendarEvent e ";
    String USER = "userId";
    String G_ID = "googleEventId";
    String G_IDS = "googleEventIds";
    String USR = "WHERE e.user.id = :" + USER + " ";
    String G_SRC = "AND e.source = com.api.calendar.CalendarEventSource.GOOGLE ";
    String G_ID_FILTER = "AND e.googleEventId = :" + G_ID;
    String G_IDS_FILTER = "AND e.googleEventId IN :" + G_IDS;
    String G_PRESENT = "AND e.googleEventId IS NOT NULL ";
    String G_NON_EMPTY = "AND e.googleEventId <> ''";
    String FETCH_LEGACY = "LEFT JOIN FETCH e.service legacyService ";
    String FETCH_LINKS = "LEFT JOIN FETCH e.serviceLinks serviceLink ";
    String FETCH_LINKED = "LEFT JOIN FETCH serviceLink.service linkedService ";
    String START = "startDate";

    @Query(SEL + USR + G_SRC + G_ID_FILTER)
    Optional<CalendarEvent> findByUserIdAndGoogleEventId(@Param(USER) Long userId,
                                                         @Param(G_ID) String googleEventId);

    @Query(SEL + USR + G_SRC + G_IDS_FILTER)
    List<CalendarEvent> findByUserIdAndGoogleEventIdIn(@Param(USER) Long userId,
                                                       @Param(G_IDS) Collection<String> googleEventIds);

    @Query(SEL_DIST + FETCH_LEGACY + FETCH_LINKS + FETCH_LINKED + USR + G_SRC)
    List<CalendarEvent> findAllWithAssociationsByUserId(@Param(USER) Long userId);

    @Query(SEL + USR + G_SRC + G_PRESENT + G_NON_EMPTY)
    List<CalendarEvent> findGoogleBackedByUserId(@Param(USER) Long userId);

    @Query(SEL + USR + G_SRC + G_PRESENT + G_NON_EMPTY + "AND e.timing.start >= :" + START)
    List<CalendarEvent> findGoogleBackedByUserIdAndEventStartGreaterThanEqual(
            @Param(USER) Long userId,
            @Param(START) Instant startDate
    );

    @Query(SEL_DIST + FETCH_LEGACY + FETCH_LINKS + FETCH_LINKED + USR + G_SRC + G_IDS_FILTER)
    List<CalendarEvent> findWithAssociationsByUserIdAndGoogleEventIdIn(@Param(USER) Long userId,
                                                                        @Param(G_IDS) Collection<String> googleEventIds);

    void deleteByUserIdAndGoogleEventId(Long userId, String googleEventId);
}
