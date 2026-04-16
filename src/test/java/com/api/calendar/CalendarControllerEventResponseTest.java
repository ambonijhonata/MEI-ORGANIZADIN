package com.api.calendar;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarControllerEventResponseTest {

    @Test
    void eventResponseFromShouldNotTouchPaymentsLazyCollection() {
        CalendarEvent event = mock(CalendarEvent.class);
        when(event.getId()).thenReturn(10L);
        when(event.getGoogleEventId()).thenReturn("g-1");
        when(event.getTitle()).thenReturn("title");
        when(event.getEventStart()).thenReturn(Instant.parse("2026-04-11T10:00:00Z"));
        when(event.getEventEnd()).thenReturn(null);
        when(event.isIdentified()).thenReturn(true);
        when(event.getServiceDescriptionSnapshot()).thenReturn("service");
        when(event.getServiceValueSnapshot()).thenReturn(new BigDecimal("123.45"));
        when(event.getPaymentType()).thenReturn(PaymentType.PIX);

        CalendarController.EventResponse response = CalendarController.EventResponse.from(event, new BigDecimal("0"));

        assertEquals(10L, response.id());
        assertEquals("g-1", response.googleEventId());
        assertEquals(List.of(), response.payments());
        assertEquals(CalendarPaymentStatus.NONE, response.paymentSummary().status());
        assertEquals(new BigDecimal("0"), response.paymentSummary().paidAmount());
        assertEquals(new BigDecimal("123.45"), response.paymentSummary().totalAmount());
        verify(event, never()).getPayments();
    }
}
