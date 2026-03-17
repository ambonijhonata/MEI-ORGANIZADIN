package com.api.report;

import com.api.calendar.*;
import com.api.user.User;
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
class CashFlowReportServiceExtendedTest {

    @Mock private CalendarEventServiceLinkRepository serviceLinkRepository;
    @Mock private SyncStateRepository syncStateRepository;

    private CashFlowReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new CashFlowReportService(serviceLinkRepository, syncStateRepository, 30);
    }

    @Test
    void shouldReturnEmptyEntriesForDaysWithNoEvents() {
        when(serviceLinkRepository.findByUserAndPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        var report = reportService.generateReport(1L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12));

        assertEquals(3, report.entries().size());
        for (var entry : report.entries()) {
            assertEquals(BigDecimal.ZERO, entry.total());
            assertTrue(entry.services().isEmpty());
        }
    }

    @Test
    void shouldBuildSyncMetadataWithReauthRequired() {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markReauthRequired("revoked");

        when(serviceLinkRepository.findByUserAndPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));

        var report = reportService.generateReport(1L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12));

        assertTrue(report.syncMetadata().reauthRequired());
    }

    @Test
    void shouldBuildSyncMetadataWithUpToDateData() {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("token");

        when(serviceLinkRepository.findByUserAndPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));

        var report = reportService.generateReport(1L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12));

        assertTrue(report.syncMetadata().dataUpToDate());
    }

    @Test
    void shouldBuildSyncMetadataWithNullLastSyncAt() {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(serviceLinkRepository.findByUserAndPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));

        var report = reportService.generateReport(1L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12));

        assertFalse(report.syncMetadata().dataUpToDate());
    }

    @Test
    void shouldIgnoreLinksOutsideDateRange() {
        Instant outsideDay = LocalDate.of(2026, 3, 9).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);

        CalendarEvent event = mock(CalendarEvent.class);
        when(event.getEventStart()).thenReturn(outsideDay);
        CalendarEventServiceLink link = mock(CalendarEventServiceLink.class);
        when(link.getCalendarEvent()).thenReturn(event);

        when(serviceLinkRepository.findByUserAndPeriod(eq(1L), any(), any())).thenReturn(List.of(link));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        var report = reportService.generateReport(1L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12));

        for (var entry : report.entries()) {
            assertEquals(BigDecimal.ZERO, entry.total());
        }
    }

    @Test
    void shouldIncludeReportDates() {
        when(serviceLinkRepository.findByUserAndPeriod(eq(1L), any(), any())).thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        var report = reportService.generateReport(1L, LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 12));

        assertEquals(LocalDate.of(2026, 3, 10), report.startDate());
        assertEquals(LocalDate.of(2026, 3, 12), report.endDate());
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
