package com.api.calendar;

import com.api.auth.AuthenticatedUser;
import com.api.common.InvalidRequestParameterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarControllerTest {

    @Mock private CalendarFacade facade;

    @Test
    void shouldTriggerSyncWithoutStartDate() {
        CalendarController controller = new CalendarController(facade);
        AuthenticatedUser user = new AuthenticatedUser(1L, "sub", "test@example.com", "Test");
        when(facade.triggerSync(1L, null))
                .thenReturn(new CalendarApiModels.SyncResponse(1, 2, 3));

        ResponseEntity<CalendarApiModels.SyncResponse> response = controller.triggerSync(user, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().created());
        assertEquals(2, response.getBody().updated());
        assertEquals(3, response.getBody().deleted());
        verify(facade).triggerSync(1L, null);
    }

    @Test
    void shouldTriggerSyncWithStartDate() {
        CalendarController controller = new CalendarController(facade);
        AuthenticatedUser user = new AuthenticatedUser(1L, "sub", "test@example.com", "Test");
        LocalDate startDate = LocalDate.of(2026, 4, 1);
        when(facade.triggerSync(1L, startDate))
                .thenReturn(new CalendarApiModels.SyncResponse(0, 1, 0));

        ResponseEntity<CalendarApiModels.SyncResponse> response = controller.triggerSync(user, startDate);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(0, response.getBody().created());
        assertEquals(1, response.getBody().updated());
        assertEquals(0, response.getBody().deleted());
        verify(facade).triggerSync(1L, startDate);
    }

    @Test
    void shouldListEventsWithAllowedSort() {
        CalendarController controller = new CalendarController(facade);
        AuthenticatedUser user = new AuthenticatedUser(1L, "sub", "test@example.com", "Test");
        Pageable pageable = mock(Pageable.class);
        when(facade.listEvents(1L, null, null, pageable)).thenReturn(Page.empty());

        ResponseEntity<Page<CalendarApiModels.EventResponse>> response = controller.listEvents(user, null, null, pageable);

        assertEquals(200, response.getStatusCode().value());
        verify(facade).listEvents(1L, null, null, pageable);
    }

    @Test
    void shouldRejectUnsupportedSortFieldForListEvents() {
        CalendarController controller = new CalendarController(facade);
        AuthenticatedUser user = new AuthenticatedUser(1L, "sub", "test@example.com", "Test");
        Pageable pageable = mock(Pageable.class);

        when(facade.listEvents(1L, null, null, pageable)).thenThrow(new InvalidRequestParameterException("sort", "bad sort"));

        assertThatThrownBy(() -> controller.listEvents(user, null, null, pageable))
                .isInstanceOf(InvalidRequestParameterException.class)
                .hasMessageContaining("sort");
    }

    @Test
    void shouldRejectOversizedPageForListEvents() {
        CalendarController controller = new CalendarController(facade);
        AuthenticatedUser user = new AuthenticatedUser(1L, "sub", "test@example.com", "Test");
        Pageable pageable = mock(Pageable.class);

        when(facade.listEvents(1L, null, null, pageable))
                .thenThrow(new InvalidRequestParameterException("size", "bad size"));

        assertThatThrownBy(() -> controller.listEvents(user, null, null, pageable))
                .isInstanceOf(InvalidRequestParameterException.class)
                .hasMessageContaining("size");
    }

    @Test
    void shouldCreateManualAppointment() {
        CalendarController controller = new CalendarController(facade);
        AuthenticatedUser user = new AuthenticatedUser(1L, "sub", "test@example.com", "Test");
        CalendarController.ManualAppointmentCreateRequest request =
                new CalendarController.ManualAppointmentCreateRequest(
                        11L,
                        LocalDate.of(2026, 5, 27),
                        java.time.LocalTime.of(14, 0),
                        java.time.LocalTime.of(14, 30),
                        List.of(21L, 22L)
                );
        when(facade.createManualAppointment(1L, request.toModel()))
                .thenReturn(new CalendarApiModels.EventResponse(
                        77L,
                        null,
                        "Maria - Corte + Escova",
                        "2026-05-27T17:00:00Z",
                        "2026-05-27T17:30:00Z",
                        true,
                        "Corte",
                        new java.math.BigDecimal("130.00"),
                        null,
                        List.of(),
                        CalendarPaymentSummary.fromAmounts(java.math.BigDecimal.ZERO, new java.math.BigDecimal("130.00"))
                ));

        ResponseEntity<CalendarApiModels.EventResponse> response =
                controller.createManualAppointment(user, request);

        assertEquals(201, response.getStatusCode().value());
        assertEquals(77L, response.getBody().entityId());
        assertEquals("Maria - Corte + Escova", response.getBody().title());
        verify(facade).createManualAppointment(1L, request.toModel());
    }
}
