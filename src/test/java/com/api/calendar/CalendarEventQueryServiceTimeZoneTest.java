package com.api.calendar;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarEventQueryServiceTimeZoneTest {
    @Mock private CalendarEventRepository eventRepository;
    @Mock private CalendarEventPaymentRepository paymentRepository;
    @Mock private SyncStateRepository stateRepository;

    @Test
    void dailyRangeUsesSaoPauloDayAndKeepsEventsBeforeAndAfter21HoursTogether() {
        final Pageable pageable = PageRequest.of(0, 50);
        final Page<CalendarEvent> emptyPage = new PageImpl<>(java.util.List.of(), pageable, 0);
        when(eventRepository.findByUserIdAndEventStartGreaterThanEqualAndEventStartLessThan(
                eq(7L), any(Instant.class), any(Instant.class), any(Pageable.class)
        )).thenReturn(emptyPage);
        final CalendarEventQueryService service = new CalendarEventQueryService(
                eventRepository, paymentRepository, stateRepository
        );

        service.listEvents(7L, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4), pageable);

        final ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        final ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(eventRepository).findByUserIdAndEventStartGreaterThanEqualAndEventStartLessThan(
                eq(7L), startCaptor.capture(), endCaptor.capture(), any(Pageable.class)
        );
        final Instant start = startCaptor.getValue();
        final Instant end = endCaptor.getValue();
        final Instant eventAt2030 = Instant.parse("2026-11-04T23:30:00Z");
        final Instant eventAt2130 = Instant.parse("2026-11-05T00:30:00Z");

        assertEquals(Instant.parse("2026-11-04T03:00:00Z"), start);
        assertEquals(Instant.parse("2026-11-05T03:00:00Z"), end);
        assertTrue(!eventAt2030.isBefore(start) && eventAt2030.isBefore(end));
        assertTrue(!eventAt2130.isBefore(start) && eventAt2130.isBefore(end));
        assertFalse(end.isBefore(eventAt2130));
    }
}
