package com.api.calendar;

import com.api.servicecatalog.Service;
import com.api.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CalendarEventServiceLinkTest {

    private final User user = new User("sub", "email@test.com", "Name");

    @Test
    void shouldSnapshotServiceDescriptionAndValue() {
        CalendarEvent event = new CalendarEvent(user, "e1", "corte", "corte", Instant.now(), Instant.now());
        Service service = new Service(user, "Corte de Cabelo", "corte de cabelo", new BigDecimal("75.00"));

        CalendarEventServiceLink link = new CalendarEventServiceLink(event, service);

        assertEquals(event, link.getCalendarEvent());
        assertEquals(service, link.getService());
        assertEquals("Corte de Cabelo", link.getServiceDescriptionSnapshot());
        assertEquals(new BigDecimal("75.00"), link.getServiceValueSnapshot());
    }
}
