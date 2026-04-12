package com.api.calendar;

import com.api.auth.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarControllerTest {

    @Mock private CalendarSyncService calendarSyncService;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private SyncStateRepository syncStateRepository;

    @Test
    void shouldTriggerSyncWithoutStartDate() {
        CalendarController controller = new CalendarController(
                calendarSyncService,
                calendarEventRepository,
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
                calendarEventRepository,
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
}
