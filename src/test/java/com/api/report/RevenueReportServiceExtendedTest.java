package com.api.report;

import com.api.calendar.*;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueReportServiceExtendedTest {

    @Mock private CalendarEventServiceLinkRepository serviceLinkRepository;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private SyncStateRepository syncStateRepository;

    private RevenueReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new RevenueReportService(serviceLinkRepository, calendarEventRepository,
                syncStateRepository, 30);
    }

    @Test
    void shouldFallbackToLegacyRevenueWhenNoLinks() {
        when(serviceLinkRepository.findByUserAndPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(calendarEventRepository.sumRevenueByUserAndPeriod(eq(1L), any(), any())).thenReturn(new BigDecimal("200.00"));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        var report = reportService.generateReport(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertEquals(new BigDecimal("200.00"), report.totalRevenue());
    }

    @Test
    void shouldNotFallbackWhenLinksExist() {
        CalendarEventServiceLink link = org.mockito.Mockito.mock(CalendarEventServiceLink.class);
        when(link.getServiceValueSnapshot()).thenReturn(new BigDecimal("100.00"));
        when(serviceLinkRepository.findByUserAndPeriod(eq(1L), any(), any())).thenReturn(List.of(link));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        var report = reportService.generateReport(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertEquals(new BigDecimal("100.00"), report.totalRevenue());
    }

    @Test
    void shouldReturnZeroRevenueWhenNoLinksAndNoLegacy() {
        when(serviceLinkRepository.findByUserAndPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(calendarEventRepository.sumRevenueByUserAndPeriod(eq(1L), any(), any())).thenReturn(BigDecimal.ZERO);
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        var report = reportService.generateReport(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertEquals(BigDecimal.ZERO, report.totalRevenue());
    }

    @Test
    void shouldBuildMetadataWithNoSyncState() {
        when(serviceLinkRepository.findByUserAndPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(calendarEventRepository.sumRevenueByUserAndPeriod(eq(1L), any(), any())).thenReturn(BigDecimal.ZERO);
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        var report = reportService.generateReport(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertFalse(report.syncMetadata().dataUpToDate());
        assertNull(report.syncMetadata().lastSyncAt());
        assertFalse(report.syncMetadata().reauthRequired());
    }

    @Test
    void shouldBuildMetadataWithNullLastSyncAt() {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(serviceLinkRepository.findByUserAndPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(calendarEventRepository.sumRevenueByUserAndPeriod(eq(1L), any(), any())).thenReturn(BigDecimal.ZERO);
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));

        var report = reportService.generateReport(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertFalse(report.syncMetadata().dataUpToDate());
    }

    @Test
    void shouldIncludeReportDates() {
        when(serviceLinkRepository.findByUserAndPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(calendarEventRepository.sumRevenueByUserAndPeriod(eq(1L), any(), any())).thenReturn(BigDecimal.ZERO);
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        var report = reportService.generateReport(1L, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15));

        assertEquals(LocalDate.of(2026, 3, 1), report.startDate());
        assertEquals(LocalDate.of(2026, 3, 15), report.endDate());
    }

    @Test
    void shouldUsePaidOnlyRepositoriesWhenPaymentScopeIsPaidOnly() {
        when(serviceLinkRepository.findByUserAndPeriodPaidOnly(eq(1L), any(), any())).thenReturn(List.of());
        when(calendarEventRepository.sumRevenueByUserAndPeriodPaidOnly(eq(1L), any(), any())).thenReturn(BigDecimal.ZERO);
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        var report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31),
                PaymentScope.PAID_ONLY
        );

        assertEquals(BigDecimal.ZERO, report.totalRevenue());
        verify(serviceLinkRepository).findByUserAndPeriodPaidOnly(eq(1L), any(), any());
        verify(calendarEventRepository).sumRevenueByUserAndPeriodPaidOnly(eq(1L), any(), any());
    }
}
