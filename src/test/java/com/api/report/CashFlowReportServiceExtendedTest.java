package com.api.report;

import com.api.calendar.CalendarEvent;
import com.api.calendar.CalendarEventPaymentRepository;
import com.api.calendar.CalendarEventPaymentTotal;
import com.api.calendar.CalendarEventRepository;
import com.api.calendar.CalendarEventServiceLink;
import com.api.calendar.PaymentType;
import com.api.calendar.SyncState;
import com.api.calendar.SyncStateRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashFlowReportServiceExtendedTest {

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
    void shouldDistributePartialPaymentProportionallyAcrossMultipleServices() {
        Instant day = LocalDate.of(2026, 3, 10).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
        CalendarEvent event = mockEventWithLinks(
                20L,
                day,
                null,
                List.of(
                        serviceLink("Sobrancelha", new BigDecimal("10.00")),
                        serviceLink("Buco", new BigDecimal("13.00"))
                )
        );

        when(calendarEventRepository.findIdentifiedWithServiceLinksByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(event));
        when(calendarEventPaymentRepository.summarizePaidAmountsByEventIdIn(any()))
                .thenReturn(List.of(new CalendarEventPaymentTotal(20L, new BigDecimal("20.00"))));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        CashFlowReportService.CashFlowReport report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 10),
                PaymentScope.PAID_ONLY
        );

        CashFlowReportService.DailyEntry entry = report.entries().get(0);
        assertEquals(new BigDecimal("20.00"), entry.total());
        assertEquals(2, entry.services().size());
        assertEquals("Buco", entry.services().get(0).name());
        assertEquals(new BigDecimal("11.30"), entry.services().get(0).total());
        assertEquals("Sobrancelha", entry.services().get(1).name());
        assertEquals(new BigDecimal("8.70"), entry.services().get(1).total());
    }

    @Test
    void shouldKeepLegacyPaidMarkerCompatibilityWithoutPaymentComposition() {
        Instant day = LocalDate.of(2026, 3, 10).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
        CalendarEvent legacyEvent = org.mockito.Mockito.mock(CalendarEvent.class);
        when(legacyEvent.getId()).thenReturn(21L);
        when(legacyEvent.getEventStart()).thenReturn(day);
        when(legacyEvent.getPaymentType()).thenReturn(PaymentType.DINHEIRO);
        when(legacyEvent.getServiceLinks()).thenReturn(List.of());
        when(legacyEvent.getServiceDescriptionSnapshot()).thenReturn("Sobrancelha");
        when(legacyEvent.getServiceValueSnapshot()).thenReturn(new BigDecimal("23.00"));

        when(calendarEventRepository.findIdentifiedWithServiceLinksByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(legacyEvent));
        when(calendarEventPaymentRepository.summarizePaidAmountsByEventIdIn(any()))
                .thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        CashFlowReportService.CashFlowReport report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 10),
                PaymentScope.PAID_ONLY
        );

        CashFlowReportService.DailyEntry entry = report.entries().get(0);
        assertEquals(new BigDecimal("23.00"), entry.total());
        assertEquals(1, entry.services().size());
        assertEquals("Sobrancelha", entry.services().get(0).name());
        assertEquals(new BigDecimal("23.00"), entry.services().get(0).total());
    }

    @Test
    void shouldNotReduceTotalsInAllScopeWhenPartialPaymentExists() {
        Instant day = LocalDate.of(2026, 3, 10).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
        CalendarEvent event = mockEventWithLinks(
                null,
                day,
                null,
                List.of(serviceLink("Sobrancelha", new BigDecimal("23.00")))
        );

        when(calendarEventRepository.findIdentifiedWithServiceLinksByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(event));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        CashFlowReportService.CashFlowReport report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 10),
                PaymentScope.ALL
        );

        CashFlowReportService.DailyEntry entry = report.entries().get(0);
        assertEquals(new BigDecimal("23.00"), entry.total());
        assertEquals(new BigDecimal("23.00"), entry.services().get(0).total());
    }

    @Test
    void shouldSortEqualTotalsByServiceNameAscending() {
        Instant day = LocalDate.of(2026, 3, 10).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
        CalendarEvent event = mockEventWithLinks(
                null,
                day,
                null,
                List.of(
                        serviceLink("Zeta", new BigDecimal("10.00")),
                        serviceLink("Alfa", new BigDecimal("10.00"))
                )
        );

        when(calendarEventRepository.findIdentifiedWithServiceLinksByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(event));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        CashFlowReportService.CashFlowReport report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 10),
                PaymentScope.ALL
        );

        CashFlowReportService.DailyEntry entry = report.entries().get(0);
        assertEquals(new BigDecimal("20.00"), entry.total());
        assertEquals(2, entry.services().size());
        assertEquals("Alfa", entry.services().get(0).name());
        assertEquals(new BigDecimal("10.00"), entry.services().get(0).total());
        assertEquals("Zeta", entry.services().get(1).name());
        assertEquals(new BigDecimal("10.00"), entry.services().get(1).total());
    }

    @Test
    void shouldBuildSyncMetadataWithReauthRequired() {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markReauthRequired("revoked");

        when(calendarEventRepository.findIdentifiedWithServiceLinksByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));

        var report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 12),
                PaymentScope.ALL
        );

        assertTrue(report.syncMetadata().reauthRequired());
    }

    @Test
    void shouldBuildSyncMetadataWithNullLastSyncAt() {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(calendarEventRepository.findIdentifiedWithServiceLinksByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));

        var report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 3, 12),
                PaymentScope.ALL
        );

        assertFalse(report.syncMetadata().dataUpToDate());
    }

    private CalendarEvent mockEventWithLinks(Long id,
                                             Instant eventStart,
                                             PaymentType paymentType,
                                             List<CalendarEventServiceLink> links) {
        CalendarEvent event = org.mockito.Mockito.mock(CalendarEvent.class);
        when(event.getEventStart()).thenReturn(eventStart);
        if (id != null) {
            when(event.getId()).thenReturn(id);
        }
        if (paymentType != null) {
            when(event.getPaymentType()).thenReturn(paymentType);
        }
        when(event.getServiceLinks()).thenReturn(links);
        return event;
    }

    private CalendarEventServiceLink serviceLink(String description, BigDecimal value) {
        CalendarEventServiceLink link = org.mockito.Mockito.mock(CalendarEventServiceLink.class);
        when(link.getServiceDescriptionSnapshot()).thenReturn(description);
        when(link.getServiceValueSnapshot()).thenReturn(value);
        return link;
    }
}
