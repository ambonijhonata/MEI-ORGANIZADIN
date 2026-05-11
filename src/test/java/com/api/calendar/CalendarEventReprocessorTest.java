package com.api.calendar;

import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarEventReprocessorTest {

    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private CalendarEventServiceLinkRepository calendarEventServiceLinkRepository;
    @Mock private CalendarEventServiceMatcher matcher;
    @Mock private EventTitleParser titleParser;
    @Mock private ServiceDescriptionNormalizer normalizer;
    @Mock private SyncStateRepository syncStateRepository;

    private CalendarEventReprocessor reprocessor;

    @BeforeEach
    void setUp() {
        reprocessor = new CalendarEventReprocessor(
                calendarEventRepository,
                calendarEventServiceLinkRepository,
                matcher,
                titleParser,
                normalizer,
                syncStateRepository,
                new UserScopedExecutionLock()
        );
    }

    @Test
    void shouldReprocessAndMatchUnidentifiedEvents() {
        User user = new User("sub", "email@test.com", "Name");
        CalendarEvent event = new CalendarEvent(user, "e1", "maria-corte+barba (pix)", "maria-corte+barba (pix)", Instant.now(), Instant.now());
        Service corte = new Service(user, "Corte", "corte", new BigDecimal("50.00"));
        Service barba = new Service(user, "Barba", "barba", new BigDecimal("30.00"));

        when(calendarEventRepository.findByUserIdAndIdentifiedFalse(1L)).thenReturn(List.of(event));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("corte", corte);
            put("barba", barba);
        }});
        when(titleParser.parse("maria-corte+barba (pix)"))
                .thenReturn(new EventTitleParser.ParsedTitle("maria", List.of("corte", "barba"), PaymentType.PIX));
        when(normalizer.normalize("corte")).thenReturn("corte");
        when(normalizer.normalize("barba")).thenReturn("barba");

        reprocessor.reprocessUnidentifiedEvents(1L);

        assertTrue(event.isIdentified());
        assertEquals(2, event.getServiceLinks().size());
        assertEquals("Corte", event.getServiceDescriptionSnapshot());
        assertEquals(new BigDecimal("80.00"), event.getServiceValueSnapshot());
        assertEquals(PaymentType.PIX, event.getPaymentType());
        verify(calendarEventRepository).saveAll(List.of(event));
        verify(calendarEventServiceLinkRepository, never()).deleteInBulkByCalendarEventIdIn(anySet());
    }

    @Test
    void shouldNotMatchWhenNoServiceFound() {
        User user = new User("sub", "email@test.com", "Name");
        CalendarEvent event = new CalendarEvent(user, "e1", "maria-unknown (boleto)", "maria-unknown (boleto)", Instant.now(), Instant.now());

        when(calendarEventRepository.findByUserIdAndIdentifiedFalse(1L)).thenReturn(List.of(event));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>());
        when(titleParser.parse("maria-unknown (boleto)"))
                .thenReturn(new EventTitleParser.ParsedTitle("maria", List.of("unknown"), null));
        when(normalizer.normalize("unknown")).thenReturn("unknown");

        reprocessor.reprocessUnidentifiedEvents(1L);

        assertFalse(event.isIdentified());
        assertNull(event.getPaymentType());
        verify(calendarEventRepository).saveAll(List.of(event));
        verify(calendarEventServiceLinkRepository, never()).deleteInBulkByCalendarEventIdIn(anySet());
    }

    @Test
    void shouldHandleEmptyUnidentifiedList() {
        when(calendarEventRepository.findByUserIdAndIdentifiedFalse(1L)).thenReturn(List.of());
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>());

        reprocessor.reprocessUnidentifiedEvents(1L);

        verify(calendarEventRepository).saveAll(List.of());
        verify(calendarEventServiceLinkRepository, never()).deleteInBulkByCalendarEventIdIn(anySet());
    }

    @Test
    void shouldKeepCanonicalOccurrenceCountAcrossRepeatedReprocessing() {
        User user = new User("sub", "email@test.com", "Name");
        CalendarEvent event = new CalendarEvent(user, "e1", "maria-corte (pix)", "maria-corte (pix)", Instant.now(), Instant.now());
        setCalendarEventId(event, 77L);
        Service corte = new Service(user, "Corte", "corte", new BigDecimal("50.00"));

        when(calendarEventRepository.findByUserIdAndIdentifiedFalse(1L)).thenReturn(List.of(event));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("corte", corte);
        }});
        when(titleParser.parse("maria-corte (pix)"))
                .thenReturn(new EventTitleParser.ParsedTitle("maria", List.of("corte"), PaymentType.PIX));
        when(normalizer.normalize("corte")).thenReturn("corte");

        reprocessor.reprocessUnidentifiedEvents(1L);
        reprocessor.reprocessUnidentifiedEvents(1L);

        assertEquals(1, event.getServiceLinks().size());
        verify(calendarEventRepository, times(2)).saveAll(List.of(event));
        verify(calendarEventServiceLinkRepository, never()).deleteInBulkByCalendarEventIdIn(anySet());
    }

    @Test
    void shouldReprocessRepeatedServiceTitleAsQuantity() {
        User user = new User("sub", "email@test.com", "Name");
        CalendarEvent event = new CalendarEvent(user, "e1", "maria-sobrancelha+sobrancelha", "maria-sobrancelha+sobrancelha", Instant.now(), Instant.now());
        Service sobrancelha = serviceWithId(user, 1L, "Sobrancelha", "sobrancelha", "48.00");

        when(calendarEventRepository.findByUserIdAndIdentifiedFalse(1L)).thenReturn(List.of(event));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("sobrancelha", sobrancelha);
        }});
        when(titleParser.parse("maria-sobrancelha+sobrancelha"))
                .thenReturn(new EventTitleParser.ParsedTitle("maria", List.of("sobrancelha", "sobrancelha"), null));
        when(normalizer.normalize("sobrancelha")).thenReturn("sobrancelha");

        reprocessor.reprocessUnidentifiedEvents(1L);

        assertTrue(event.isIdentified());
        assertEquals(2, event.getServiceLinks().size());
        assertEquals(0, event.getServiceLinks().get(0).getOccurrenceIndex());
        assertEquals(1, event.getServiceLinks().get(1).getOccurrenceIndex());
        assertEquals(new BigDecimal("96.00"), event.getServiceValueSnapshot());
    }

    @Test
    void shouldEnrichPartiallyIdentifiedSynchronizedAppointments() {
        User user = new User("sub", "email@test.com", "Name");
        CalendarEvent event = new CalendarEvent(
                user,
                "e1",
                "fulano - sobrancelha + buco + tintura",
                "fulano - sobrancelha + buco + tintura",
                Instant.now(),
                Instant.now()
        );
        setCalendarEventId(event, 88L);
        Service sobrancelha = serviceWithId(user, 1L, "Sobrancelha", "sobrancelha", "48.00");
        Service buco = serviceWithId(user, 2L, "Buco", "buco", "23.00");
        Service tintura = serviceWithId(user, 3L, "Tintura", "tintura", "35.00");
        event.associateServices(List.of(sobrancelha));

        when(calendarEventRepository.findAllWithAssociationsByUserId(1L)).thenReturn(List.of(event));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("sobrancelha", sobrancelha);
            put("buco", buco);
            put("tintura", tintura);
        }});
        when(titleParser.parse("fulano - sobrancelha + buco + tintura"))
                .thenReturn(new EventTitleParser.ParsedTitle("fulano", List.of("sobrancelha", "buco", "tintura"), null));
        when(normalizer.normalize("sobrancelha")).thenReturn("sobrancelha");
        when(normalizer.normalize("buco")).thenReturn("buco");
        when(normalizer.normalize("tintura")).thenReturn("tintura");
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(new SyncState(user)));

        reprocessor.enrichSynchronizedAppointments(1L);

        assertTrue(event.isIdentified());
        assertEquals(3, event.getServiceLinks().size());
        assertEquals("Sobrancelha", event.getServiceDescriptionSnapshot());
        assertEquals(new BigDecimal("106.00"), event.getServiceValueSnapshot());
        verify(calendarEventRepository).saveAll(List.of(event));
        verify(calendarEventServiceLinkRepository, never()).deleteInBulkByCalendarEventIdIn(anySet());
    }

    @Test
    void shouldSkipPersistWhenCatalogChangeAddsNoNewService() {
        User user = new User("sub", "email@test.com", "Name");
        CalendarEvent event = new CalendarEvent(user, "e1", "fulano - sobrancelha", "fulano - sobrancelha", Instant.now(), Instant.now());
        Service sobrancelha = serviceWithId(user, 1L, "Sobrancelha", "sobrancelha", "48.00");
        event.associateServices(List.of(sobrancelha));

        when(calendarEventRepository.findAllWithAssociationsByUserId(1L)).thenReturn(List.of(event));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("sobrancelha", sobrancelha);
        }});
        when(titleParser.parse("fulano - sobrancelha"))
                .thenReturn(new EventTitleParser.ParsedTitle("fulano", List.of("sobrancelha"), null));
        when(normalizer.normalize("sobrancelha")).thenReturn("sobrancelha");
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(new SyncState(user)));

        reprocessor.enrichSynchronizedAppointments(1L);

        verify(calendarEventRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldMarkPendingCatalogEnrichmentAsAppliedAfterSuccessfulEnrichment() {
        User user = new User("sub", "email@test.com", "Name");
        CalendarEvent event = new CalendarEvent(user, "e1", "fulano - sobrancelha + buco", "fulano - sobrancelha + buco", Instant.now(), Instant.now());
        Service sobrancelha = serviceWithId(user, 1L, "Sobrancelha", "sobrancelha", "48.00");
        Service buco = serviceWithId(user, 2L, "Buco", "buco", "23.00");
        event.associateServices(List.of(sobrancelha));
        SyncState syncState = new SyncState(user);
        syncState.requestCatalogEnrichment();

        when(calendarEventRepository.findAllWithAssociationsByUserId(1L)).thenReturn(List.of(event));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("sobrancelha", sobrancelha);
            put("buco", buco);
        }});
        when(titleParser.parse("fulano - sobrancelha + buco"))
                .thenReturn(new EventTitleParser.ParsedTitle("fulano", List.of("sobrancelha", "buco"), null));
        when(normalizer.normalize("sobrancelha")).thenReturn("sobrancelha");
        when(normalizer.normalize("buco")).thenReturn("buco");

        boolean enriched = reprocessor.enrichPendingSynchronizedAppointments(1L, syncState);

        assertTrue(enriched);
        assertFalse(syncState.hasPendingCatalogEnrichment());
        assertEquals(1L, syncState.getCatalogEnrichmentRevisionApplied());
        verify(syncStateRepository).save(syncState);
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

    private void setCalendarEventId(CalendarEvent event, Long eventId) {
        try {
            var idField = CalendarEvent.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(event, eventId);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to set CalendarEvent id for test setup", e);
        }
    }
}
