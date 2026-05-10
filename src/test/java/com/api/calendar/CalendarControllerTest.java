package com.api.calendar;

import com.api.auth.AuthenticatedUser;
import com.api.common.InvalidRequestParameterException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarControllerTest {

    @Mock private CalendarSyncService calendarSyncService;
    @Mock private CalendarPaymentService calendarPaymentService;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private CalendarEventPaymentRepository calendarEventPaymentRepository;
    @Mock private SyncStateRepository syncStateRepository;

    @Test
    void shouldTriggerSyncWithoutStartDate() {
        CalendarController controller = new CalendarController(
                calendarSyncService,
                calendarPaymentService,
                calendarEventRepository,
                calendarEventPaymentRepository,
                syncStateRepository
        );
        AuthenticatedUser user = new AuthenticatedUser(1L, "sub", "test@example.com", "Test");
        when(calendarSyncService.synchronize(1L, null))
                .thenReturn(new CalendarSyncService.SyncResult(1, 2, 3));

        ResponseEntity<CalendarController.SyncResponse> response = controller.triggerSync(user, null);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().created());
        assertEquals(2, response.getBody().updated());
        assertEquals(3, response.getBody().deleted());
        verify(calendarSyncService).synchronize(1L, null);
    }

    @Test
    void shouldTriggerSyncWithStartDate() {
        CalendarController controller = new CalendarController(
                calendarSyncService,
                calendarPaymentService,
                calendarEventRepository,
                calendarEventPaymentRepository,
                syncStateRepository
        );
        AuthenticatedUser user = new AuthenticatedUser(1L, "sub", "test@example.com", "Test");
        LocalDate startDate = LocalDate.of(2026, 4, 1);
        when(calendarSyncService.synchronize(1L, startDate))
                .thenReturn(new CalendarSyncService.SyncResult(0, 1, 0));

        ResponseEntity<CalendarController.SyncResponse> response = controller.triggerSync(user, startDate);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(0, response.getBody().created());
        assertEquals(1, response.getBody().updated());
        assertEquals(0, response.getBody().deleted());
        verify(calendarSyncService).synchronize(1L, startDate);
    }

    @Test
    void shouldListEventsWithAllowedSort() {
        CalendarController controller = new CalendarController(
                calendarSyncService,
                calendarPaymentService,
                calendarEventRepository,
                calendarEventPaymentRepository,
                syncStateRepository
        );
        AuthenticatedUser user = new AuthenticatedUser(1L, "sub", "test@example.com", "Test");
        PageRequest pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "eventStart"));
        Page<CalendarEvent> page = new PageImpl<>(List.of(), pageable, 0);
        when(calendarEventRepository.findByUserId(1L, pageable)).thenReturn(page);

        ResponseEntity<Page<CalendarController.EventResponse>> response = controller.listEvents(user, null, null, pageable);

        assertEquals(200, response.getStatusCode().value());
        verify(calendarEventRepository).findByUserId(1L, pageable);
    }

    @Test
    void shouldRejectUnsupportedSortFieldForListEvents() {
        CalendarController controller = new CalendarController(
                calendarSyncService,
                calendarPaymentService,
                calendarEventRepository,
                calendarEventPaymentRepository,
                syncStateRepository
        );
        AuthenticatedUser user = new AuthenticatedUser(1L, "sub", "test@example.com", "Test");
        PageRequest pageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "eventStart;delete"));

        assertThatThrownBy(() -> controller.listEvents(user, null, null, pageable))
                .isInstanceOf(InvalidRequestParameterException.class)
                .hasMessageContaining("sort");
    }

    @Test
    void shouldRejectOversizedPageForListEvents() {
        CalendarController controller = new CalendarController(
                calendarSyncService,
                calendarPaymentService,
                calendarEventRepository,
                calendarEventPaymentRepository,
                syncStateRepository
        );
        AuthenticatedUser user = new AuthenticatedUser(1L, "sub", "test@example.com", "Test");
        PageRequest pageable = PageRequest.of(0, 101);

        assertThatThrownBy(() -> controller.listEvents(user, null, null, pageable))
                .isInstanceOf(InvalidRequestParameterException.class)
                .hasMessageContaining("size");
    }
}
