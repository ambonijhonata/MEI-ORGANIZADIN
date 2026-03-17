package com.api.calendar;

import com.api.servicecatalog.Service;
import com.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarEventReprocessorTest {

    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private CalendarEventServiceMatcher matcher;

    private CalendarEventReprocessor reprocessor;

    @BeforeEach
    void setUp() {
        reprocessor = new CalendarEventReprocessor(calendarEventRepository, matcher);
    }

    @Test
    void shouldReprocessAndMatchUnidentifiedEvents() {
        User user = new User("sub", "email@test.com", "Name");
        CalendarEvent event = new CalendarEvent(user, "e1", "corte", "corte", Instant.now(), Instant.now());
        Service service = new Service(user, "Corte", "corte", new BigDecimal("50.00"));

        when(calendarEventRepository.findByUserIdAndIdentifiedFalse(1L)).thenReturn(List.of(event));
        when(matcher.matchService(1L, "corte")).thenReturn(Optional.of(service));

        reprocessor.reprocessUnidentifiedEvents(1L);

        assertTrue(event.isIdentified());
        assertEquals("Corte", event.getServiceDescriptionSnapshot());
        verify(calendarEventRepository).saveAll(List.of(event));
    }

    @Test
    void shouldNotMatchWhenNoServiceFound() {
        User user = new User("sub", "email@test.com", "Name");
        CalendarEvent event = new CalendarEvent(user, "e1", "unknown", "unknown", Instant.now(), Instant.now());

        when(calendarEventRepository.findByUserIdAndIdentifiedFalse(1L)).thenReturn(List.of(event));
        when(matcher.matchService(1L, "unknown")).thenReturn(Optional.empty());

        reprocessor.reprocessUnidentifiedEvents(1L);

        assertFalse(event.isIdentified());
        verify(calendarEventRepository).saveAll(List.of(event));
    }

    @Test
    void shouldHandleEmptyUnidentifiedList() {
        when(calendarEventRepository.findByUserIdAndIdentifiedFalse(1L)).thenReturn(List.of());

        reprocessor.reprocessUnidentifiedEvents(1L);

        verify(calendarEventRepository).saveAll(List.of());
    }
}
