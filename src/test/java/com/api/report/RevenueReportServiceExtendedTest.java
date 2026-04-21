package com.api.report;

import com.api.calendar.CalendarEvent;
import com.api.calendar.CalendarEventPaymentRepository;
import com.api.calendar.CalendarEventRepository;
import com.api.calendar.SyncState;
import com.api.calendar.SyncStateRepository;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueReportServiceExtendedTest {

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
    void shouldReturnZeroWhenEventHasNoPaymentInfoInPaidOnlyScope() {
        CalendarEvent unpaidEvent = org.mockito.Mockito.mock(CalendarEvent.class);
        when(unpaidEvent.getId()).thenReturn(30L);
        when(unpaidEvent.getServiceValueSnapshot()).thenReturn(new BigDecimal("23.00"));
        when(unpaidEvent.getPaymentType()).thenReturn(null);

        when(calendarEventRepository.findIdentifiedByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(unpaidEvent));
        when(calendarEventPaymentRepository.summarizePaidAmountsByEventIdIn(any()))
                .thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        var report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                PaymentScope.PAID_ONLY
        );

        assertEquals(BigDecimal.ZERO, report.totalRevenue());
    }

    @Test
    void shouldBuildMetadataWithNoSyncState() {
        when(calendarEventRepository.findIdentifiedByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        var report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                PaymentScope.ALL
        );

        assertFalse(report.syncMetadata().dataUpToDate());
        assertNull(report.syncMetadata().lastSyncAt());
        assertFalse(report.syncMetadata().reauthRequired());
    }

    @Test
    void shouldBuildMetadataWithNullLastSyncAt() {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(calendarEventRepository.findIdentifiedByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));

        var report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                PaymentScope.ALL
        );

        assertFalse(report.syncMetadata().dataUpToDate());
    }

    @Test
    void shouldMarkReauthRequired() {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markReauthRequired("revoked");

        when(calendarEventRepository.findIdentifiedByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));

        var report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                PaymentScope.ALL
        );

        assertTrue(report.syncMetadata().reauthRequired());
    }

    @Test
    void shouldIncludeReportDates() {
        when(calendarEventRepository.findIdentifiedByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        var report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 15),
                PaymentScope.ALL
        );

        assertEquals(LocalDate.of(2026, 3, 1), report.startDate());
        assertEquals(LocalDate.of(2026, 3, 15), report.endDate());
    }
}
