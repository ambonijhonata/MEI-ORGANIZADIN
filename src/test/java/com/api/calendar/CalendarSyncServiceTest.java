package com.api.calendar;

import com.api.client.ClientService;
import com.api.common.IntegrationRevokedException;
import com.api.google.GoogleCalendarClient;
import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.user.User;
import com.api.user.UserRepository;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarSyncServiceTest {

    @Mock private GoogleCalendarClient googleCalendarClient;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private SyncStateRepository syncStateRepository;
    @Mock private CalendarEventServiceMatcher matcher;
    @Mock private ServiceDescriptionNormalizer normalizer;
    @Mock private UserRepository userRepository;
    @Mock private EventTitleParser titleParser;
    @Mock private ClientService clientService;

    private CalendarSyncService syncService;

    @BeforeEach
    void setUp() {
        syncService = new CalendarSyncService(googleCalendarClient, calendarEventRepository,
                syncStateRepository, matcher, normalizer, userRepository, titleParser, clientService);
    }

    @Test
    void shouldPerformFullSyncWhenNoSyncStateExists() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event event = createTestEvent("event-1", "maria - corte");
        when(googleCalendarClient.fetchEvents(eq(1L), isNull()))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "sync-token-1"));
        when(normalizer.normalize(anyString())).thenReturn("maria - corte");
        when(calendarEventRepository.findByUserIdAndGoogleEventId(1L, "event-1")).thenReturn(Optional.empty());
        when(titleParser.parse("maria - corte")).thenReturn(
                new EventTitleParser.ParsedTitle("maria", List.of("corte")));
        when(matcher.matchService(eq(1L), eq("corte"))).thenReturn(Optional.empty());
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(1, result.created());
        assertEquals(0, result.deleted());
    }

    @Test
    void shouldPerformIncrementalSyncWithSyncToken() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("old-sync-token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        when(googleCalendarClient.fetchEvents(1L, "old-sync-token"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(), "new-sync-token"));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.deleted());
    }

    @Test
    void shouldDeleteLocalEventWhenGoogleEventCancelled() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("sync-token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event deletedEvent = new Event().setId("deleted-1").setStatus("cancelled");
        CalendarEvent localEvent = new CalendarEvent(user, "deleted-1", "Test", "test",
                java.time.Instant.now(), java.time.Instant.now());
        when(googleCalendarClient.fetchEvents(1L, "sync-token"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(deletedEvent), "new-token"));
        when(calendarEventRepository.findByUserIdAndGoogleEventId(1L, "deleted-1"))
                .thenReturn(Optional.of(localEvent));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(1, result.deleted());
        verify(calendarEventRepository).delete(localEvent);
    }

    @Test
    void shouldThrowWhenIntegrationIsRevoked() {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markReauthRequired("Token revoked");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));

        assertThrows(IntegrationRevokedException.class, () -> syncService.synchronize(1L));
    }

    @Test
    void shouldFallbackToFullResyncOn410() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("expired-token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        when(googleCalendarClient.fetchEvents(1L, "expired-token"))
                .thenThrow(new GoogleCalendarClient.SyncTokenExpiredException("410 Gone"));
        when(googleCalendarClient.fetchEvents(eq(1L), isNull()))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(), "new-token"));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertNotNull(result);
    }

    private Event createTestEvent(String id, String summary) {
        return new Event()
                .setId(id)
                .setSummary(summary)
                .setStatus("confirmed")
                .setStart(new EventDateTime().setDateTime(new DateTime(System.currentTimeMillis())))
                .setEnd(new EventDateTime().setDateTime(new DateTime(System.currentTimeMillis() + 3600000)));
    }
}
