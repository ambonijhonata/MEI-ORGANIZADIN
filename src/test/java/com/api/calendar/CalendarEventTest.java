package com.api.calendar;

import com.api.client.Client;
import com.api.servicecatalog.Service;
import com.api.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CalendarEventTest {

    private final User user = new User("sub", "email@test.com", "Name");

    @Test
    void shouldAssociateSingleService() {
        CalendarEvent event = new CalendarEvent(user, "e1", "corte", "corte", Instant.now(), Instant.now());
        Service service = new Service(user, "Corte", "corte", new BigDecimal("50.00"));

        event.associateService(service);

        assertTrue(event.isIdentified());
        assertEquals(service, event.getService());
        assertEquals("Corte", event.getServiceDescriptionSnapshot());
        assertEquals(new BigDecimal("50.00"), event.getServiceValueSnapshot());
    }

    @Test
    void shouldAssociateMultipleServices() {
        CalendarEvent event = new CalendarEvent(user, "e1", "sobrancelha + buço", "sobrancelha + buco", Instant.now(), Instant.now());
        Service s1 = new Service(user, "Sobrancelha", "sobrancelha", new BigDecimal("40.00"));
        Service s2 = new Service(user, "Buço", "buco", new BigDecimal("30.00"));

        event.associateServices(List.of(s1, s2));

        assertTrue(event.isIdentified());
        assertEquals(2, event.getServiceLinks().size());
        assertEquals(s1, event.getService());
        assertEquals("Sobrancelha", event.getServiceDescriptionSnapshot());
        assertEquals(new BigDecimal("70.00"), event.getServiceValueSnapshot());
    }

    @Test
    void shouldAssociateEmptyServicesList() {
        CalendarEvent event = new CalendarEvent(user, "e1", "test", "test", Instant.now(), Instant.now());

        event.associateServices(List.of());

        assertFalse(event.isIdentified());
        assertTrue(event.getServiceLinks().isEmpty());
    }

    @Test
    void shouldEnrichExistingServicesWithoutOverwritingOriginalSnapshot() {
        CalendarEvent event = new CalendarEvent(user, "e1", "helena - sobrancelha + buÃ§o + tintura",
                "helena - sobrancelha + buco + tintura", Instant.now(), Instant.now());
        Service sobrancelha = new Service(user, "Sobrancelha", "sobrancelha", new BigDecimal("48.00"));
        Service buco = new Service(user, "BuÃ§o", "buco", new BigDecimal("23.00"));
        Service tintura = new Service(user, "Tintura", "tintura", new BigDecimal("35.00"));

        event.associateServices(List.of(sobrancelha));
        boolean changed = event.enrichServices(List.of(sobrancelha, buco, tintura));

        assertTrue(changed);
        assertTrue(event.isIdentified());
        assertEquals("Sobrancelha", event.getServiceDescriptionSnapshot());
        assertEquals(new BigDecimal("106.00"), event.getServiceValueSnapshot());
        assertEquals(3, event.getServiceLinks().size());
        assertEquals("Sobrancelha", event.getServiceLinks().get(0).getServiceDescriptionSnapshot());
        assertEquals(new BigDecimal("48.00"), event.getServiceLinks().get(0).getServiceValueSnapshot());
        assertEquals("BuÃ§o", event.getServiceLinks().get(1).getServiceDescriptionSnapshot());
        assertEquals(new BigDecimal("23.00"), event.getServiceLinks().get(1).getServiceValueSnapshot());
        assertEquals("Tintura", event.getServiceLinks().get(2).getServiceDescriptionSnapshot());
        assertEquals(new BigDecimal("35.00"), event.getServiceLinks().get(2).getServiceValueSnapshot());
    }

    @Test
    void shouldNotChangeWhenEnrichingWithEquivalentServices() {
        CalendarEvent event = new CalendarEvent(user, "e1", "rodrigo - sobrancelha",
                "rodrigo - sobrancelha", Instant.now(), Instant.now());
        Service sobrancelha = new Service(user, "Sobrancelha", "sobrancelha", new BigDecimal("48.00"));

        event.associateServices(List.of(sobrancelha));
        boolean changed = event.enrichServices(List.of(sobrancelha));

        assertFalse(changed);
        assertEquals(1, event.getServiceLinks().size());
        assertEquals(new BigDecimal("48.00"), event.getServiceValueSnapshot());
    }

    @Test
    void shouldClearServiceAssociation() {
        CalendarEvent event = new CalendarEvent(user, "e1", "corte", "corte", Instant.now(), Instant.now());
        Service service = new Service(user, "Corte", "corte", new BigDecimal("50.00"));
        event.associateService(service);

        event.clearServiceAssociation();

        assertFalse(event.isIdentified());
        assertNull(event.getService());
        assertNull(event.getServiceDescriptionSnapshot());
        assertNull(event.getServiceValueSnapshot());
        assertTrue(event.getServiceLinks().isEmpty());
    }

    @Test
    void shouldUpdateFromGoogle() {
        CalendarEvent event = new CalendarEvent(user, "e1", "old", "old", Instant.now(), Instant.now());
        Instant newStart = Instant.now().plusSeconds(3600);
        Instant newEnd = Instant.now().plusSeconds(7200);

        event.updateFromGoogle("new title", "new title", newStart, newEnd);

        assertEquals("new title", event.getTitle());
        assertEquals("new title", event.getNormalizedTitle());
        assertEquals(newStart, event.getEventStart());
        assertEquals(newEnd, event.getEventEnd());
    }

    @Test
    void shouldSetClient() {
        CalendarEvent event = new CalendarEvent(user, "e1", "test", "test", Instant.now(), Instant.now());
        Client client = new Client(user, "Maria", "maria");

        event.setClient(client);

        assertEquals(client, event.getClient());
    }

    @Test
    void shouldMarkIdentified() {
        CalendarEvent event = new CalendarEvent(user, "e1", "test", "test", Instant.now(), Instant.now());

        event.markIdentified(true);
        assertTrue(event.isIdentified());

        event.markIdentified(false);
        assertFalse(event.isIdentified());
    }

    @Test
    void shouldSetAndKeepPaymentTypeWhenClearingServiceAssociation() {
        CalendarEvent event = new CalendarEvent(user, "e1", "test", "test", Instant.now(), Instant.now());
        Service service = new Service(user, "Corte", "corte", new BigDecimal("50.00"));
        event.associateService(service);

        event.setPaymentType(PaymentType.PIX);
        event.clearServiceAssociation();

        assertEquals(PaymentType.PIX, event.getPaymentType());
        event.setPaymentType(null);
        assertNull(event.getPaymentType());
    }

    @Test
    void shouldReturnCorrectFieldsFromConstructor() {
        Instant start = Instant.now();
        Instant end = Instant.now().plusSeconds(3600);
        CalendarEvent event = new CalendarEvent(user, "g-123", "Title", "title", start, end);

        assertEquals(user, event.getUser());
        assertEquals("g-123", event.getGoogleEventId());
        assertEquals("Title", event.getTitle());
        assertEquals("title", event.getNormalizedTitle());
        assertEquals(start, event.getEventStart());
        assertEquals(end, event.getEventEnd());
        assertFalse(event.isIdentified());
        assertNull(event.getClient());
        assertNull(event.getService());
    }

    @Test
    void shouldAssociateRepeatedSameServiceAsSeparateOccurrences() {
        CalendarEvent event = new CalendarEvent(user, "e1", "sobrancelha + sobrancelha", "sobrancelha + sobrancelha", Instant.now(), Instant.now());
        Service sobrancelha = new Service(user, "Sobrancelha", "sobrancelha", new BigDecimal("40.00"));

        event.associateServices(List.of(sobrancelha, sobrancelha));

        assertTrue(event.isIdentified());
        assertEquals(2, event.getServiceLinks().size());
        assertEquals(0, event.getServiceLinks().get(0).getOccurrenceIndex());
        assertEquals(1, event.getServiceLinks().get(1).getOccurrenceIndex());
        assertEquals(new BigDecimal("80.00"), event.getServiceValueSnapshot());
    }

    @Test
    void shouldEnrichExistingServicesWithMissingRepeatedOccurrence() {
        CalendarEvent event = new CalendarEvent(user, "e1", "rodrigo - sobrancelha + sobrancelha",
                "rodrigo - sobrancelha + sobrancelha", Instant.now(), Instant.now());
        Service sobrancelha = new Service(user, "Sobrancelha", "sobrancelha", new BigDecimal("48.00"));

        event.associateServices(List.of(sobrancelha));
        boolean changed = event.enrichServices(List.of(sobrancelha, sobrancelha));

        assertTrue(changed);
        assertEquals(2, event.getServiceLinks().size());
        assertEquals(0, event.getServiceLinks().get(0).getOccurrenceIndex());
        assertEquals(1, event.getServiceLinks().get(1).getOccurrenceIndex());
        assertEquals(new BigDecimal("96.00"), event.getServiceValueSnapshot());
    }
}
