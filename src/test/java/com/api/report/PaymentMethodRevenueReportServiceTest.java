package com.api.report;

import com.api.calendar.CalendarEvent;
import com.api.calendar.CalendarEventPaymentMethodTotal;
import com.api.calendar.CalendarEventPaymentRepository;
import com.api.calendar.CalendarEventRepository;
import com.api.calendar.PaymentType;
import com.api.calendar.SyncStateRepository;
import com.api.common.InvalidPeriodException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentMethodRevenueReportServiceTest {

    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private CalendarEventPaymentRepository calendarEventPaymentRepository;
    @Mock private SyncStateRepository syncStateRepository;

    private PaymentMethodRevenueReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new PaymentMethodRevenueReportService(
                calendarEventRepository,
                calendarEventPaymentRepository,
                syncStateRepository,
                30
        );
    }

    @Test
    void shouldAggregatePersistedPaymentsByMethodInCanonicalOrder() {
        CalendarEvent firstEvent = mockEvent(10L, PaymentType.CREDITO);
        CalendarEvent secondEvent = mockEvent(11L, PaymentType.PIX);

        when(calendarEventRepository.findIdentifiedByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(firstEvent, secondEvent));
        when(calendarEventPaymentRepository.summarizePaidAmountsByPaymentTypeForEventIdIn(List.of(10L, 11L)))
                .thenReturn(List.of(
                        new CalendarEventPaymentMethodTotal(PaymentType.PIX, new BigDecimal("20.00")),
                        new CalendarEventPaymentMethodTotal(PaymentType.DINHEIRO, new BigDecimal("40.00")),
                        new CalendarEventPaymentMethodTotal(PaymentType.CREDITO, new BigDecimal("15.50"))
                ));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        var report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );

        assertEquals(List.of("DINHEIRO", "PIX", "DEBITO", "CREDITO"),
                report.entries().stream().map(PaymentMethodRevenueReportService.PaymentMethodRevenueEntry::paymentType).toList());
        assertEquals(new BigDecimal("40.00"), report.entries().get(0).total());
        assertEquals(new BigDecimal("20.00"), report.entries().get(1).total());
        assertEquals(new BigDecimal("0.00"), report.entries().get(2).total());
        assertEquals(new BigDecimal("15.50"), report.entries().get(3).total());
    }

    @Test
    void shouldIgnoreLegacyEventPaymentTypeWhenNoPersistedPaymentsExist() {
        CalendarEvent legacyEvent = mockEvent(22L, PaymentType.PIX);

        when(calendarEventRepository.findIdentifiedByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(legacyEvent));
        when(calendarEventPaymentRepository.summarizePaidAmountsByPaymentTypeForEventIdIn(List.of(22L)))
                .thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        var report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );

        assertEquals(
                List.of(new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00")),
                report.entries().stream().map(PaymentMethodRevenueReportService.PaymentMethodRevenueEntry::total).toList()
        );
    }

    @Test
    void shouldReturnZerosForAllMethodsWhenNoEventsExist() {
        when(calendarEventRepository.findIdentifiedByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        var report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31)
        );

        assertEquals(
                List.of(new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00")),
                report.entries().stream().map(PaymentMethodRevenueReportService.PaymentMethodRevenueEntry::total).toList()
        );
    }

    @Test
    void shouldRejectPeriodExceeding12Months() {
        assertThrows(InvalidPeriodException.class, () ->
                reportService.generateReport(1L, LocalDate.of(2025, 1, 1), LocalDate.of(2026, 3, 1)));
    }

    @Test
    void shouldRejectStartDateAfterEndDate() {
        assertThrows(InvalidPeriodException.class, () ->
                reportService.generateReport(1L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1)));
    }

    private CalendarEvent mockEvent(Long id, PaymentType paymentType) {
        CalendarEvent event = org.mockito.Mockito.mock(CalendarEvent.class);
        lenient().when(event.getId()).thenReturn(id);
        lenient().when(event.getPaymentType()).thenReturn(paymentType);
        return event;
    }
}
