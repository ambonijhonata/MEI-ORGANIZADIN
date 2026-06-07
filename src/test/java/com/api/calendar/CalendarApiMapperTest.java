package com.api.calendar;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
class CalendarApiMapperTest {

    @Test
    void eventResponseFromShouldPreserveEventReadModelPayload() {
        CalendarEventReadModel event = new CalendarEventReadModel(
                10L,
                "g-1",
                "title",
                "2026-04-11T10:00:00Z",
                null,
                true,
                "service",
                new BigDecimal("123.45"),
                PaymentType.PIX.name(),
                BigDecimal.ZERO
        );

        CalendarApiModels.EventResponse response = CalendarApiMapper.toEventResponse(event);

        assertEquals(10L, response.entityId());
        assertEquals("g-1", response.googleEventId());
        assertEquals(List.of(), response.payments());
        assertEquals(CalendarPaymentStatus.NONE, response.paymentSummary().status());
        assertEquals(BigDecimal.ZERO, response.paymentSummary().paidAmount());
        assertEquals(new BigDecimal("123.45"), response.paymentSummary().totalAmount());
    }

    @Test
    void eventResponseFromShouldAllowNullGoogleEventIdForManualAppointment() {
        CalendarEventReadModel event = new CalendarEventReadModel(
                11L,
                null,
                "manual",
                "2026-05-27T17:00:00Z",
                "2026-05-27T17:30:00Z",
                true,
                "Corte",
                new BigDecimal("50.00"),
                null,
                null
        );

        CalendarApiModels.EventResponse response = CalendarApiMapper.toEventResponse(event);

        assertEquals(11L, response.entityId());
        assertEquals(null, response.googleEventId());
        assertEquals(CalendarPaymentStatus.NONE, response.paymentSummary().status());
    }
}
