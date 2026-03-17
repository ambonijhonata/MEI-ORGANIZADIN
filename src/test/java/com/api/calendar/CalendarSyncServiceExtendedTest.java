package com.api.calendar;

import com.api.client.Client;
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
class CalendarSyncServiceExtendedTest {

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
    void shouldThrowWhenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> syncService.synchronize(99L));
    }

    @Test
    void shouldMarkReauthRequiredOnOAuthRevocation() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);
        when(googleCalendarClient.fetchEvents(eq(1L), isNull()))
                .thenThrow(new GoogleCalendarClient.OAuthRevokedException("Token revoked"));

        assertThrows(IntegrationRevokedException.class, () -> syncService.synchronize(1L));
        assertEquals(SyncStatus.REAUTH_REQUIRED, syncState.getStatus());
    }

    @Test
    void shouldMarkFailedOnIOException() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);
        when(googleCalendarClient.fetchEvents(eq(1L), isNull()))
                .thenThrow(new IOException("Network error"));

        assertThrows(RuntimeException.class, () -> syncService.synchronize(1L));
        assertEquals(SyncStatus.SYNC_FAILED, syncState.getStatus());
        assertEquals("IO_ERROR", syncState.getErrorCategory());
    }

    @Test
    void shouldProcessEventWithClientAndMatchedServices() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event event = createTestEvent("e1", "maria - corte + barba");
        when(googleCalendarClient.fetchEvents(eq(1L), isNull()))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "token"));
        when(normalizer.normalize(anyString())).thenReturn("maria - corte + barba");
        when(calendarEventRepository.findByUserIdAndGoogleEventId(1L, "e1")).thenReturn(Optional.empty());
        when(titleParser.parse("maria - corte + barba"))
                .thenReturn(new EventTitleParser.ParsedTitle("maria", List.of("corte", "barba")));

        Client client = new Client(user, "Maria", "maria");
        when(clientService.findOrCreateByName(eq(1L), eq(user), eq("maria"))).thenReturn(client);

        Service s1 = new Service(user, "Corte", "corte", new BigDecimal("50.00"));
        Service s2 = new Service(user, "Barba", "barba", new BigDecimal("30.00"));
        when(matcher.matchService(1L, "corte")).thenReturn(Optional.of(s1));
        when(matcher.matchService(1L, "barba")).thenReturn(Optional.of(s2));
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(1, result.created());
        verify(clientService).findOrCreateByName(eq(1L), eq(user), eq("maria"));
    }

    @Test
    void shouldUpdateExistingEventOnIncrementalSync() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("old-token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event event = createTestEvent("e1", "corte");
        when(googleCalendarClient.fetchEvents(1L, "old-token"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "new-token"));
        when(normalizer.normalize(anyString())).thenReturn("corte");

        CalendarEvent existingEvent = new CalendarEvent(user, "e1", "old", "old",
                java.time.Instant.now(), java.time.Instant.now());
        when(calendarEventRepository.findByUserIdAndGoogleEventId(1L, "e1")).thenReturn(Optional.of(existingEvent));
        when(titleParser.parse("corte")).thenReturn(new EventTitleParser.ParsedTitle(null, List.of("corte")));
        when(matcher.matchService(1L, "corte")).thenReturn(Optional.empty());
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(0, result.created());
        assertEquals(1, result.updated());
    }

    @Test
    void shouldProcessEventWithNoClientInTitle() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event event = createTestEvent("e1", "corte");
        when(googleCalendarClient.fetchEvents(eq(1L), isNull()))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "token"));
        when(normalizer.normalize(anyString())).thenReturn("corte");
        when(calendarEventRepository.findByUserIdAndGoogleEventId(1L, "e1")).thenReturn(Optional.empty());
        when(titleParser.parse("corte")).thenReturn(new EventTitleParser.ParsedTitle(null, List.of("corte")));
        when(matcher.matchService(1L, "corte")).thenReturn(Optional.empty());
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        syncService.synchronize(1L);

        verify(clientService, never()).findOrCreateByName(anyLong(), any(), anyString());
    }

    @Test
    void shouldHandleEventWithDateOnlyTime() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event event = new Event()
                .setId("e1")
                .setSummary("test")
                .setStatus("confirmed")
                .setStart(new EventDateTime().setDate(new DateTime("2026-03-15")))
                .setEnd(new EventDateTime().setDate(new DateTime("2026-03-16")));

        when(googleCalendarClient.fetchEvents(eq(1L), isNull()))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "token"));
        when(normalizer.normalize(anyString())).thenReturn("test");
        when(calendarEventRepository.findByUserIdAndGoogleEventId(1L, "e1")).thenReturn(Optional.empty());
        when(titleParser.parse("test")).thenReturn(new EventTitleParser.ParsedTitle(null, List.of("test")));
        when(matcher.matchService(1L, "test")).thenReturn(Optional.empty());
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(1, result.created());
    }

    @Test
    void shouldHandleEventWithNullStartEnd() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event event = new Event()
                .setId("e1")
                .setSummary("test")
                .setStatus("confirmed");

        when(googleCalendarClient.fetchEvents(eq(1L), isNull()))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "token"));
        when(normalizer.normalize(anyString())).thenReturn("test");
        when(calendarEventRepository.findByUserIdAndGoogleEventId(1L, "e1")).thenReturn(Optional.empty());
        when(titleParser.parse("test")).thenReturn(new EventTitleParser.ParsedTitle(null, List.of("test")));
        when(matcher.matchService(1L, "test")).thenReturn(Optional.empty());
        when(calendarEventRepository.save(any(CalendarEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(1, result.created());
    }

    @Test
    void shouldDeleteNonExistentLocalEventGracefully() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event deletedEvent = new Event().setId("gone-1").setStatus("cancelled");
        when(googleCalendarClient.fetchEvents(1L, "token"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(deletedEvent), "new-token"));
        when(calendarEventRepository.findByUserIdAndGoogleEventId(1L, "gone-1")).thenReturn(Optional.empty());

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(1, result.deleted());
        verify(calendarEventRepository, never()).delete(any());
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
