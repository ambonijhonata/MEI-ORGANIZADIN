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
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueReportServiceTest {

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
    void shouldGenerateRevenueReportFromServiceLinks() {
        CalendarEventServiceLink link1 = mockLink(new BigDecimal("50.00"));
        CalendarEventServiceLink link2 = mockLink(new BigDecimal("30.00"));

        when(serviceLinkRepository.findByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(link1, link2));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        RevenueReportService.RevenueReport report = reportService.generateReport(
                1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertEquals(new BigDecimal("80.00"), report.totalRevenue());
        assertFalse(report.syncMetadata().dataUpToDate());
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

        when(serviceLinkRepository.findByUserAndPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(calendarEventRepository.sumRevenueByUserAndPeriod(eq(1L), any(), any())).thenReturn(BigDecimal.ZERO);
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));

        RevenueReportService.RevenueReport report = reportService.generateReport(
                1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertTrue(report.syncMetadata().dataUpToDate());
    }

    @Test
    void shouldMarkReauthRequired() {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markReauthRequired("revoked");

        when(serviceLinkRepository.findByUserAndPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(calendarEventRepository.sumRevenueByUserAndPeriod(eq(1L), any(), any())).thenReturn(BigDecimal.ZERO);
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));

        RevenueReportService.RevenueReport report = reportService.generateReport(
                1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertTrue(report.syncMetadata().reauthRequired());
    }

    private CalendarEventServiceLink mockLink(BigDecimal value) {
        CalendarEventServiceLink link = org.mockito.Mockito.mock(CalendarEventServiceLink.class);
        when(link.getServiceValueSnapshot()).thenReturn(value);
        return link;
    }
}
