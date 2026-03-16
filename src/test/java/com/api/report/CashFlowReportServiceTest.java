package com.api.report;

import com.api.calendar.*;
import com.api.common.InvalidPeriodException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashFlowReportServiceTest {

    @Mock private CalendarEventServiceLinkRepository serviceLinkRepository;
    @Mock private SyncStateRepository syncStateRepository;

    private CashFlowReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new CashFlowReportService(serviceLinkRepository, syncStateRepository, 30);
    }

    @Test
    void shouldGenerateDailyEntriesWithServiceBreakdown() {
        Instant day1Start = LocalDate.of(2026, 3, 10).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
        Instant day2Start = LocalDate.of(2026, 3, 11).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(7200);

        CalendarEventServiceLink link1 = mockLink(day1Start, "Corte", new BigDecimal("50.00"));
        CalendarEventServiceLink link2 = mockLink(day1Start, "Corte", new BigDecimal("50.00"));
        CalendarEventServiceLink link3 = mockLink(day1Start, "Barba", new BigDecimal("30.00"));
        CalendarEventServiceLink link4 = mockLink(day2Start, "Corte", new BigDecimal("50.00"));

        when(serviceLinkRepository.findByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(link1, link2, link3, link4));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        CashFlowReportService.CashFlowReport report = reportService.generateReport(
                1L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12));

        assertEquals(3, report.entries().size());

        // Day 10: 2x Corte (100) + 1x Barba (30) = 130
        CashFlowReportService.DailyEntry day1 = report.entries().get(0);
        assertEquals(new BigDecimal("130.00"), day1.total());
        assertEquals(2, day1.services().size());
        assertEquals("Barba", day1.services().get(0).name());
        assertEquals(new BigDecimal("30.00"), day1.services().get(0).total());
        assertEquals("Corte", day1.services().get(1).name());
        assertEquals(new BigDecimal("100.00"), day1.services().get(1).total());

        // Day 11: 1x Corte (50)
        CashFlowReportService.DailyEntry day2 = report.entries().get(1);
        assertEquals(new BigDecimal("50.00"), day2.total());
        assertEquals(1, day2.services().size());

        // Day 12: empty
        CashFlowReportService.DailyEntry day3 = report.entries().get(2);
        assertEquals(BigDecimal.ZERO, day3.total());
        assertEquals(0, day3.services().size());
    }

    @Test
    void shouldRejectPeriodExceeding7Days() {
        assertThrows(InvalidPeriodException.class, () ->
                reportService.generateReport(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15)));
    }

    @Test
    void shouldRejectStartDateAfterEndDate() {
        assertThrows(InvalidPeriodException.class, () ->
                reportService.generateReport(1L, LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 10)));
    }

    private CalendarEventServiceLink mockLink(Instant eventStart, String serviceName, BigDecimal value) {
        CalendarEvent event = mock(CalendarEvent.class);
        when(event.getEventStart()).thenReturn(eventStart);

        CalendarEventServiceLink link = mock(CalendarEventServiceLink.class);
        when(link.getCalendarEvent()).thenReturn(event);
        when(link.getServiceDescriptionSnapshot()).thenReturn(serviceName);
        when(link.getServiceValueSnapshot()).thenReturn(value);
        return link;
    }
}
