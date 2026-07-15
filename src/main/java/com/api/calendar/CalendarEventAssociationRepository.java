package com.api.calendar;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CalendarEventAssociationRepository {
    String ID_SEL = "SELECT e.id AS calendarEventId, " +
            "s.id AS serviceId, " +
            "s.normalizedText AS serviceNormalizedDescription, " +
            "s.description AS serviceDescription, " +
            "s.value AS serviceValue " +
            "FROM CalendarEvent e " +
            "JOIN e.service s ";
    String EVT_IDS = "WHERE e.id IN :calendarEventIds";

    boolean existsByServiceId(Long serviceId);

    boolean existsByClientId(Long clientId);

    @Query(ID_SEL + EVT_IDS)
    List<CalendarEventRepository.ServiceIdentityRow> findLegacyServiceIdentityRowsByCalendarEventIdIn(
            @Param("calendarEventIds") Collection<Long> calendarEventIds
    );
}
