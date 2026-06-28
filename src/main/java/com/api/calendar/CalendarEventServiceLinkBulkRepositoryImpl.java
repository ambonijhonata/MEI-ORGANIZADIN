package com.api.calendar;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;

@Repository
public class CalendarEventServiceLinkBulkRepositoryImpl implements CalendarEventServiceLinkBulkRepository {

    private final EntityManager entityManager;

    public CalendarEventServiceLinkBulkRepositoryImpl(final EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void deleteInBulkByCalendarEventIdIn(final Collection<Long> calendarEventIds) {
        if (calendarEventIds == null || calendarEventIds.isEmpty()) {
            return;
        }

        final Query query = entityManager.createQuery(
                "DELETE FROM CalendarEventServiceLink sl WHERE sl.calendarEvent.id IN :calendarEventIds"
        );
        query.setFlushMode(FlushModeType.COMMIT);
        query.setParameter("calendarEventIds", calendarEventIds);
        query.executeUpdate();
    }
}
