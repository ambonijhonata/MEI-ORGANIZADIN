package com.api.calendar;

import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarEventServiceLinkBulkRepositoryImplTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    private CalendarEventServiceLinkBulkRepositoryImpl repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new CalendarEventServiceLinkBulkRepositoryImpl();
        Field entityManagerField = CalendarEventServiceLinkBulkRepositoryImpl.class.getDeclaredField("entityManager");
        entityManagerField.setAccessible(true);
        entityManagerField.set(repository, entityManager);
    }

    @Test
    void shouldExecuteBulkDeleteWithoutAutoFlush() {
        when(entityManager.createQuery(
                "DELETE FROM CalendarEventServiceLink sl WHERE sl.calendarEvent.id IN :calendarEventIds"
        )).thenReturn(query);
        when(query.setFlushMode(FlushModeType.COMMIT)).thenReturn(query);
        when(query.setParameter("calendarEventIds", List.of(10L, 11L))).thenReturn(query);

        repository.deleteInBulkByCalendarEventIdIn(List.of(10L, 11L));

        verify(query).setFlushMode(FlushModeType.COMMIT);
        verify(query).setParameter("calendarEventIds", List.of(10L, 11L));
        verify(query).executeUpdate();
    }

    @Test
    void shouldSkipBulkDeleteWhenIdsAreEmpty() {
        repository.deleteInBulkByCalendarEventIdIn(List.of());

        verify(entityManager, never()).createQuery(org.mockito.ArgumentMatchers.anyString());
    }
}
