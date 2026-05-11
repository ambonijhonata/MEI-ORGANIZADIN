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
import com.api.servicecatalog.Service;
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
        assertEquals(1, entry.services().get(0).quantity());
        assertEquals(new BigDecimal("11.30"), entry.services().get(0).total());
        assertEquals("Sobrancelha", entry.services().get(1).name());
        assertEquals(1, entry.services().get(1).quantity());
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
        assertEquals(1, entry.services().get(0).quantity());
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
        assertEquals(1, entry.services().get(0).quantity());
        assertEquals(new BigDecimal("10.00"), entry.services().get(0).total());
        assertEquals("Zeta", entry.services().get(1).name());
        assertEquals(1, entry.services().get(1).quantity());
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

    @Test
    void shouldKeepSingleServiceTotalAfterRepeatedCanonicalAssociationUpdates() {
        User user = new User("sub", "email@test.com", "Name");
        Service sobrancelha = new Service(user, "Sobrancelha", "sobrancelha", new BigDecimal("48.00"));
        Instant day = LocalDate.of(2026, 4, 11).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
        CalendarEvent event = new CalendarEvent(user, "e1", "rodrigo - sobrancelha", "rodrigo - sobrancelha", day, day.plusSeconds(1800));
        event.associateServices(List.of(sobrancelha));
        event.associateServices(List.of(sobrancelha));

        when(calendarEventRepository.findIdentifiedWithServiceLinksByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(event));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        CashFlowReportService.CashFlowReport report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 4, 11),
                LocalDate.of(2026, 4, 11),
                PaymentScope.ALL
        );

        CashFlowReportService.DailyEntry entry = report.entries().get(0);
        assertEquals(new BigDecimal("48.00"), entry.total());
        assertEquals(1, entry.services().size());
        assertEquals("Sobrancelha", entry.services().get(0).name());
        assertEquals(1, entry.services().get(0).quantity());
        assertEquals(new BigDecimal("48.00"), entry.services().get(0).total());
    }

    @Test
    void shouldKeepMultiServiceTotalAfterRepeatedCanonicalAssociationUpdates() {
        User user = new User("sub", "email@test.com", "Name");
        Service sobrancelha = new Service(user, "Sobrancelha", "sobrancelha", new BigDecimal("48.00"));
        Service buco = new Service(user, "Buco", "buco", new BigDecimal("23.00"));
        Instant day = LocalDate.of(2026, 4, 11).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(7200);
        CalendarEvent event = new CalendarEvent(user, "e2", "helena - sobrancelha + buco", "helena - sobrancelha + buco", day, day.plusSeconds(1800));
        event.associateServices(List.of(sobrancelha, buco));
        event.associateServices(List.of(sobrancelha, buco));

        when(calendarEventRepository.findIdentifiedWithServiceLinksByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(event));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        CashFlowReportService.CashFlowReport report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 4, 11),
                LocalDate.of(2026, 4, 11),
                PaymentScope.ALL
        );

        CashFlowReportService.DailyEntry entry = report.entries().get(0);
        assertEquals(new BigDecimal("71.00"), entry.total());
        assertEquals(2, entry.services().size());
        assertEquals("Sobrancelha", entry.services().get(0).name());
        assertEquals(1, entry.services().get(0).quantity());
        assertEquals(new BigDecimal("48.00"), entry.services().get(0).total());
        assertEquals("Buco", entry.services().get(1).name());
        assertEquals(1, entry.services().get(1).quantity());
        assertEquals(new BigDecimal("23.00"), entry.services().get(1).total());
    }

    @Test
    void shouldKeepEnrichedAppointmentTotalAlignedWithLateCreatedServices() {
        User user = new User("sub", "email@test.com", "Name");
        Service sobrancelha = serviceWithId(user, 1L, "Sobrancelha", "sobrancelha", "48.00");
        Service buco = serviceWithId(user, 2L, "Buco", "buco", "23.00");
        Service tintura = serviceWithId(user, 3L, "Tintura", "tintura", "35.00");
        Instant day = LocalDate.of(2026, 4, 12).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(5400);
        CalendarEvent event = new CalendarEvent(user, "e3", "fulano - sobrancelha + buco + tintura",
                "fulano - sobrancelha + buco + tintura", day, day.plusSeconds(1800));
        event.associateServices(List.of(sobrancelha));
        event.enrichServices(List.of(sobrancelha, buco, tintura));

        when(calendarEventRepository.findIdentifiedWithServiceLinksByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(event));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        CashFlowReportService.CashFlowReport report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 4, 12),
                LocalDate.of(2026, 4, 12),
                PaymentScope.ALL
        );

        CashFlowReportService.DailyEntry entry = report.entries().get(0);
        assertEquals(new BigDecimal("106.00"), entry.total());
        assertEquals(3, entry.services().size());
        assertEquals("Sobrancelha", entry.services().get(0).name());
        assertEquals(new BigDecimal("48.00"), entry.services().get(0).total());
        assertEquals("Tintura", entry.services().get(1).name());
        assertEquals(new BigDecimal("35.00"), entry.services().get(1).total());
        assertEquals("Buco", entry.services().get(2).name());
        assertEquals(new BigDecimal("23.00"), entry.services().get(2).total());
    }

    @Test
    void shouldReflectRepeatedSameServiceQuantityInCashFlow() {
        User user = new User("sub", "email@test.com", "Name");
        Service sobrancelha = new Service(user, "Sobrancelha", "sobrancelha", new BigDecimal("48.00"));
        Service buco = new Service(user, "Buco", "buco", new BigDecimal("23.00"));
        Instant day = LocalDate.of(2026, 4, 13).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
        CalendarEvent event = new CalendarEvent(user, "e4", "maria - sobrancelha + sobrancelha + buco",
                "maria - sobrancelha + sobrancelha + buco", day, day.plusSeconds(1800));
        event.associateServices(List.of(sobrancelha, sobrancelha, buco));

        when(calendarEventRepository.findIdentifiedWithServiceLinksByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(event));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        CashFlowReportService.CashFlowReport report = reportService.generateReport(
                1L,
                LocalDate.of(2026, 4, 13),
                LocalDate.of(2026, 4, 13),
                PaymentScope.ALL
        );

        CashFlowReportService.DailyEntry entry = report.entries().get(0);
        assertEquals(new BigDecimal("119.00"), entry.total());
        assertEquals(2, entry.services().size());
        assertEquals("Sobrancelha", entry.services().get(0).name());
        assertEquals(2, entry.services().get(0).quantity());
        assertEquals(new BigDecimal("96.00"), entry.services().get(0).total());
        assertEquals("Buco", entry.services().get(1).name());
        assertEquals(1, entry.services().get(1).quantity());
        assertEquals(new BigDecimal("23.00"), entry.services().get(1).total());
    }

    @Test
    void shouldCountOnlyContributionsReturnedInPaidOnlyScope() {
        Instant day = LocalDate.of(2026, 3, 10).atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(3600);
        CalendarEvent paidEvent = mockEventWithLinks(
                30L,
                day,
                null,
                List.of(serviceLink("Sobrancelha", new BigDecimal("23.00")))
        );
        CalendarEvent unpaidEvent = mockEventWithLinks(
                31L,
                day.plusSeconds(3600),
                null,
                List.of(serviceLink("Sobrancelha", new BigDecimal("25.00")))
        );

        when(calendarEventRepository.findIdentifiedWithServiceLinksByUserAndPeriod(eq(1L), any(), any()))
                .thenReturn(List.of(paidEvent, unpaidEvent));
        when(calendarEventPaymentRepository.summarizePaidAmountsByEventIdIn(any()))
                .thenReturn(List.of(new CalendarEventPaymentTotal(30L, new BigDecimal("23.00"))));
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
        assertEquals(1, entry.services().get(0).quantity());
        assertEquals(new BigDecimal("23.00"), entry.services().get(0).total());
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

    private Service serviceWithId(User user, Long id, String description, String normalized, String value) {
        Service service = new Service(user, description, normalized, new BigDecimal(value));
        try {
            var idField = Service.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(service, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to set Service id for test setup", e);
        }
        return service;
    }
}
