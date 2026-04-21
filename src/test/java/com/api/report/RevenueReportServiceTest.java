package com.api.report;

import com.api.calendar.CalendarEvent;
import com.api.calendar.CalendarEventPaymentRepository;
import com.api.calendar.CalendarEventPaymentTotal;
import com.api.calendar.CalendarEventRepository;
import com.api.calendar.PaymentType;
import com.api.calendar.SyncState;
import com.api.calendar.SyncStateRepository;
import com.api.common.InvalidPeriodException;
import com.api.user.User;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueReportServiceTest {

    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private CalendarEventPaymentRepository calendarEventPaymentRepository;
    @Mock private SyncStateRepository syncStateRepository;

    private RevenueReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new RevenueReportService(
                calendarEventRepository,
                new ReportPaidAmountService(calendarEventPaymentRepository),
                syncStateRepository,
                30
        );
    }

    @Test
    void shouldUseFullServiceValuesForAllScope() {
        CalendarEvent firstEvent = mockEvent(null, new BigDecimal("50.00"), null);
        CalendarEvent secondEvent = mockEvent(null, new BigDecimal("30.00"), null);

        when(calendarEventRepository.findIdentifiedByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(firstEvent, secondEvent));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        RevenueReportService.RevenueReport report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                PaymentScope.ALL
        );

        assertEquals(new BigDecimal("80.00"), report.totalRevenue());
        assertFalse(report.syncMetadata().dataUpToDate());
    }

    @Test
    void shouldUsePaidAmountForPartialPaymentInPaidOnlyScope() {
        CalendarEvent event = mockEvent(10L, new BigDecimal("23.00"), null);

        when(calendarEventRepository.findIdentifiedByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(event));
        when(calendarEventPaymentRepository.summarizePaidAmountsByEventIdIn(any()))
                .thenReturn(List.of(new CalendarEventPaymentTotal(10L, new BigDecimal("20.00"))));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        RevenueReportService.RevenueReport report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                PaymentScope.PAID_ONLY
        );

        assertEquals(new BigDecimal("20.00"), report.totalRevenue());
    }

    @Test
    void shouldFallbackToLegacyPaymentTypeWhenNoPaymentCompositionExists() {
        CalendarEvent legacyPaidEvent = mockEvent(11L, new BigDecimal("23.00"), PaymentType.PIX);

        when(calendarEventRepository.findIdentifiedByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(legacyPaidEvent));
        when(calendarEventPaymentRepository.summarizePaidAmountsByEventIdIn(any()))
                .thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        RevenueReportService.RevenueReport report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                PaymentScope.PAID_ONLY
        );

        assertEquals(new BigDecimal("23.00"), report.totalRevenue());
    }

    @Test
    void shouldNotReduceTotalsInAllScopeWhenPartialPaymentExists() {
        CalendarEvent event = mockEvent(null, new BigDecimal("23.00"), null);

        when(calendarEventRepository.findIdentifiedByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(event));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        RevenueReportService.RevenueReport report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                PaymentScope.ALL
        );

        assertEquals(new BigDecimal("23.00"), report.totalRevenue());
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

    @Test
    void shouldMarkDataAsUpToDateWhenRecentSync() {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("token");

        when(calendarEventRepository.findIdentifiedByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));

        RevenueReportService.RevenueReport report = reportService.generateReport(
                1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), PaymentScope.PAID_ONLY);

        assertTrue(report.syncMetadata().dataUpToDate());
    }

    private CalendarEvent mockEvent(Long id, BigDecimal totalValue, PaymentType paymentType) {
        CalendarEvent event = org.mockito.Mockito.mock(CalendarEvent.class);
        when(event.getServiceValueSnapshot()).thenReturn(totalValue);
        if (id != null) {
            lenient().when(event.getId()).thenReturn(id);
        }
        if (paymentType != null) {
            lenient().when(event.getPaymentType()).thenReturn(paymentType);
        }
        return event;
    }
}
