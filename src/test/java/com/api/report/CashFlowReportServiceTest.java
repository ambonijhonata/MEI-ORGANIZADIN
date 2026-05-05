package com.api.report;

import com.api.calendar.CalendarEvent;
import com.api.calendar.CalendarEventPaymentRepository;
import com.api.calendar.CalendarEventPaymentTotal;
import com.api.calendar.CalendarEventRepository;
import com.api.calendar.CalendarEventServiceLink;
import com.api.calendar.SyncStateRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashFlowReportServiceTest {

    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private CalendarEventPaymentRepository calendarEventPaymentRepository;
    @Mock private SyncStateRepository syncStateRepository;

    private CashFlowReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new CashFlowReportService(
                calendarEventRepository,
                new ReportPaidAmountService(calendarEventPaymentRepository),
                syncStateRepository,
                30
        );
    }

    @Test
    void shouldGenerateDailyEntriesWithServiceBreakdownInAllScope() {
        Instant day1 = LocalDate.of(2026, 3, 10).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
        Instant day2 = LocalDate.of(2026, 3, 11).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(7200);

        CalendarEvent firstEvent = mockEventWithLinks(
                null,
                day1,
                null,
                orderedServices(Map.of(
                        "Corte", new BigDecimal("100.00"),
                        "Barba", new BigDecimal("30.00")
                ))
        );
        CalendarEvent secondEvent = mockEventWithLinks(
                null,
                day2,
                null,
                orderedServices(Map.of("Corte", new BigDecimal("50.00")))
        );

        when(calendarEventRepository.findIdentifiedWithServiceLinksByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(firstEvent, secondEvent));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        CashFlowReportService.CashFlowReport report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 12),
                PaymentScope.ALL
        );

        assertEquals(3, report.entries().size());

        CashFlowReportService.DailyEntry day1Entry = report.entries().get(0);
        assertEquals(new BigDecimal("130.00"), day1Entry.total());
        assertEquals(2, day1Entry.services().size());
        assertEquals("Corte", day1Entry.services().get(0).name());
        assertEquals(new BigDecimal("100.00"), day1Entry.services().get(0).total());
        assertEquals("Barba", day1Entry.services().get(1).name());
        assertEquals(new BigDecimal("30.00"), day1Entry.services().get(1).total());

        CashFlowReportService.DailyEntry day2Entry = report.entries().get(1);
        assertEquals(new BigDecimal("50.00"), day2Entry.total());
        assertEquals(1, day2Entry.services().size());
        assertEquals("Corte", day2Entry.services().get(0).name());
        assertEquals(new BigDecimal("50.00"), day2Entry.services().get(0).total());

        CashFlowReportService.DailyEntry day3Entry = report.entries().get(2);
        assertEquals(BigDecimal.ZERO, day3Entry.total());
        assertEquals(0, day3Entry.services().size());
    }

    @Test
    void shouldUsePaidAmountForSingleServicePartialPaymentInPaidOnlyScope() {
        Instant day = LocalDate.of(2026, 3, 10).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
        CalendarEvent event = mockEventWithLinks(
                10L,
                day,
                null,
                orderedServices(Map.of("Sobrancelha", new BigDecimal("23.00")))
        );

        when(calendarEventRepository.findIdentifiedWithServiceLinksByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(event));
        when(calendarEventPaymentRepository.summarizePaidAmountsByEventIdIn(any()))
                .thenReturn(List.of(new CalendarEventPaymentTotal(10L, new BigDecimal("20.00"))));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        CashFlowReportService.CashFlowReport report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 10),
                PaymentScope.PAID_ONLY
        );

        CashFlowReportService.DailyEntry dayEntry = report.entries().get(0);
        assertEquals(new BigDecimal("20.00"), dayEntry.total());
        assertEquals(1, dayEntry.services().size());
        assertEquals("Sobrancelha", dayEntry.services().get(0).name());
        assertEquals(new BigDecimal("20.00"), dayEntry.services().get(0).total());
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

    private CalendarEvent mockEventWithLinks(Long id,
                                             Instant eventStart,
                                             com.api.calendar.PaymentType paymentType,
                                             Map<String, BigDecimal> services) {
        CalendarEvent event = org.mockito.Mockito.mock(CalendarEvent.class);
        when(event.getEventStart()).thenReturn(eventStart);
        if (id != null) {
            when(event.getId()).thenReturn(id);
        }
        if (paymentType != null) {
            when(event.getPaymentType()).thenReturn(paymentType);
        }

        List<CalendarEventServiceLink> links = services.entrySet().stream()
                .map(entry -> {
                    CalendarEventServiceLink link = org.mockito.Mockito.mock(CalendarEventServiceLink.class);
                    when(link.getServiceDescriptionSnapshot()).thenReturn(entry.getKey());
                    when(link.getServiceValueSnapshot()).thenReturn(entry.getValue());
                    return link;
                })
                .toList();
        when(event.getServiceLinks()).thenReturn(links);
        return event;
    }

    private Map<String, BigDecimal> orderedServices(Map<String, BigDecimal> values) {
        Map<String, BigDecimal> ordered = new LinkedHashMap<>();
        values.forEach(ordered::put);
        return ordered;
    }
}
