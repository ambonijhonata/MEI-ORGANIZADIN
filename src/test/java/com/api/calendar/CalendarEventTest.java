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
}
