package com.api.calendar;

import com.api.client.Client;
import com.api.client.ClientService;
import com.api.common.GoogleApiAccessDeniedException;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarSyncServiceExtendedTest {

    @Mock private GoogleCalendarClient googleCalendarClient;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private CalendarEventPaymentRepository calendarEventPaymentRepository;
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
                syncStateRepository, matcher, normalizer, userRepository, titleParser, clientService, calendarEventPaymentRepository);

        lenient().when(calendarEventRepository.findWithAssociationsByUserIdAndGoogleEventIdIn(anyLong(), anyCollection()))
                .thenReturn(List.of());
        lenient().when(calendarEventRepository.findAllWithAssociationsByUserId(anyLong()))
                .thenReturn(List.of());
        lenient().when(calendarEventRepository.findGoogleBackedByUserId(anyLong()))
                .thenReturn(List.of());
        lenient().when(calendarEventRepository.findGoogleBackedByUserIdAndEventStartGreaterThanEqual(anyLong(), any(Instant.class)))
                .thenReturn(List.of());
        lenient().when(clientService.clientsByNormalizedName(anyLong())).thenReturn(new HashMap<>());
        lenient().when(matcher.servicesByNormalizedDescription(anyLong())).thenReturn(new HashMap<>());
        lenient().when(calendarEventRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
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

        IntegrationRevokedException ex = assertThrows(IntegrationRevokedException.class, () -> syncService.synchronize(1L));
        assertEquals(SyncStatus.REAUTH_REQUIRED, syncState.getStatus());
        assertEquals("Token revoked", ex.getMessage());
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
    void shouldMarkFailedOnGoogleForbiddenAndExposeMessage() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);
        when(googleCalendarClient.fetchEvents(eq(1L), isNull()))
                .thenThrow(new GoogleCalendarClient.GoogleApiForbiddenException(
                        "Google Calendar API has not been used in project (reason: accessNotConfigured)"));

        GoogleApiAccessDeniedException ex = assertThrows(
                GoogleApiAccessDeniedException.class,
                () -> syncService.synchronize(1L)
        );

        assertEquals("GOOGLE_API_FORBIDDEN", syncState.getErrorCategory());
        assertEquals(SyncStatus.SYNC_FAILED, syncState.getStatus());
        assertTrue(ex.getMessage().contains("accessNotConfigured"));
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
        when(titleParser.parse("maria - corte + barba"))
                .thenReturn(new EventTitleParser.ParsedTitle("maria", List.of("corte", "barba"), null));

        Client client = new Client(user, "Maria", "maria");
        when(clientService.findOrCreateByName(eq(1L), eq(user), eq("maria"))).thenReturn(client);

        Service s1 = new Service(user, "Corte", "corte", new BigDecimal("50.00"));
        Service s2 = new Service(user, "Barba", "barba", new BigDecimal("30.00"));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("corte", s1);
            put("barba", s2);
        }});

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
        when(calendarEventRepository.findWithAssociationsByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(existingEvent));
        when(titleParser.parse("corte")).thenReturn(new EventTitleParser.ParsedTitle(null, List.of("corte"), null));

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
        when(titleParser.parse("corte")).thenReturn(new EventTitleParser.ParsedTitle(null, List.of("corte"), null));

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
        when(titleParser.parse("test")).thenReturn(new EventTitleParser.ParsedTitle(null, List.of("test"), null));

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
        when(titleParser.parse("test")).thenReturn(new EventTitleParser.ParsedTitle(null, List.of("test"), null));

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

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(0, result.deleted());
        verify(calendarEventRepository, never()).deleteAllInBatch(anyList());
    }

    @Test
    void shouldSkipPersistingWhenExistingEventIsSemanticallyUnchanged() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("old-token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event event = createTestEvent("e1", "corte");
        when(googleCalendarClient.fetchEvents(1L, "old-token"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "new-token"));
        when(normalizer.normalize("corte")).thenReturn("corte");
        when(titleParser.parse("corte")).thenReturn(new EventTitleParser.ParsedTitle(null, List.of("corte"), null));

        CalendarEvent existingEvent = new CalendarEvent(
                user,
                "e1",
                "corte",
                "corte",
                java.time.Instant.ofEpochMilli(event.getStart().getDateTime().getValue()),
                java.time.Instant.ofEpochMilli(event.getEnd().getDateTime().getValue())
        );

        when(calendarEventRepository.findWithAssociationsByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(existingEvent));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(0, result.created());
        assertEquals(1, result.updated());
        assertEquals(0, result.deleted());
        verify(calendarEventRepository, never()).saveAll(anyList());
        verify(calendarEventRepository, never()).flush();
    }

    @Test
    void shouldSkipServiceAssociationRewriteWhenEquivalent() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("old-token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event event = createTestEvent("e1", "maria - corte + barba");
        when(googleCalendarClient.fetchEvents(1L, "old-token"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "new-token"));

        when(normalizer.normalize("maria - corte + barba")).thenReturn("maria - corte + barba");
        when(normalizer.normalize("maria")).thenReturn("maria");
        when(normalizer.normalize("corte")).thenReturn("corte");
        when(normalizer.normalize("barba")).thenReturn("barba");
        when(titleParser.parse("maria - corte + barba"))
                .thenReturn(new EventTitleParser.ParsedTitle("maria", List.of("corte", "barba"), null));

        Client maria = new Client(user, "Maria", "maria");
        when(clientService.findOrCreateByName(1L, user, "maria")).thenReturn(maria);

        Service corte = new Service(user, "Corte", "corte", new BigDecimal("50.00"));
        Service barba = new Service(user, "Barba", "barba", new BigDecimal("30.00"));

        CalendarEvent existingEvent = new CalendarEvent(
                user,
                "e1",
                "maria - corte + barba",
                "maria - corte + barba",
                java.time.Instant.ofEpochMilli(event.getStart().getDateTime().getValue()),
                java.time.Instant.ofEpochMilli(event.getEnd().getDateTime().getValue())
        );
        existingEvent.setClient(maria);
        existingEvent.associateServices(List.of(corte, barba));

        when(calendarEventRepository.findWithAssociationsByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(existingEvent));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("corte", corte);
            put("barba", barba);
        }});

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(1, result.updated());
        verify(calendarEventRepository, never()).saveAll(anyList());
        verify(calendarEventRepository, never()).flush();
    }

    @Test
    void shouldAssociateOnlyKnownServicesWhenTitleContainsUnknownOnes() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event event = createTestEvent("e1", "maria - corte + inexistente + barba");
        when(googleCalendarClient.fetchEvents(eq(1L), isNull()))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "token"));
        when(normalizer.normalize(anyString())).thenAnswer(inv -> {
            String value = inv.getArgument(0);
            return value == null ? "" : value.toLowerCase();
        });
        when(titleParser.parse("maria - corte + inexistente + barba"))
                .thenReturn(new EventTitleParser.ParsedTitle("maria", List.of("corte", "inexistente", "barba"), null));

        Client client = new Client(user, "Maria", "maria");
        when(clientService.findOrCreateByName(eq(1L), eq(user), eq("maria"))).thenReturn(client);

        Service corte = new Service(user, "Corte", "corte", new BigDecimal("50.00"));
        Service barba = new Service(user, "Barba", "barba", new BigDecimal("30.00"));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("corte", corte);
            put("barba", barba);
        }});

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(1, result.created());
        verify(calendarEventRepository).saveAll(argThat(events -> {
            if (events == null) {
                return false;
            }
            List<CalendarEvent> persistedEvents = StreamSupport.stream(events.spliterator(), false).toList();
            if (persistedEvents.size() != 1) {
                return false;
            }
            CalendarEvent persisted = persistedEvents.get(0);
            return persisted.isIdentified()
                    && persisted.getServiceLinks().size() == 2
                    && persisted.getServiceValueSnapshot() != null
                    && persisted.getServiceValueSnapshot().compareTo(new BigDecimal("80.00")) == 0;
        }));
    }

    @Test
    void shouldPersistPaymentTypeForNewEvents() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event event = createTestEvent("e1", "maria - corte (pix)");
        when(googleCalendarClient.fetchEvents(eq(1L), isNull()))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "token"));
        when(normalizer.normalize(anyString())).thenAnswer(inv -> {
            String value = inv.getArgument(0);
            return value == null ? "" : value.toLowerCase();
        });
        when(titleParser.parse("maria - corte (pix)"))
                .thenReturn(new EventTitleParser.ParsedTitle("maria", List.of("corte"), PaymentType.PIX));

        Client client = new Client(user, "Maria", "maria");
        when(clientService.findOrCreateByName(eq(1L), eq(user), eq("maria"))).thenReturn(client);

        Service corte = new Service(user, "Corte", "corte", new BigDecimal("50.00"));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("corte", corte);
        }});

        syncService.synchronize(1L);

        verify(calendarEventRepository).saveAll(argThat(events -> {
            if (events == null) {
                return false;
            }
            List<CalendarEvent> persistedEvents = StreamSupport.stream(events.spliterator(), false).toList();
            return persistedEvents.size() == 1
                    && persistedEvents.get(0).getPaymentType() == PaymentType.PIX;
        }));
    }

    @Test
    void shouldUpdateExistingEventWhenOnlyPaymentTypeChanges() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("old-token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event event = createTestEvent("e1", "maria - corte (pix)");
        when(googleCalendarClient.fetchEvents(1L, "old-token"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "new-token"));
        when(normalizer.normalize(anyString())).thenAnswer(inv -> {
            String value = inv.getArgument(0);
            return value == null ? "" : value.toLowerCase();
        });
        when(titleParser.parse("maria - corte (pix)"))
                .thenReturn(new EventTitleParser.ParsedTitle("maria", List.of("corte"), PaymentType.PIX));

        Client client = new Client(user, "Maria", "maria");
        when(clientService.findOrCreateByName(eq(1L), eq(user), eq("maria"))).thenReturn(client);

        Service corte = new Service(user, "Corte", "corte", new BigDecimal("50.00"));
        CalendarEvent existingEvent = new CalendarEvent(
                user,
                "e1",
                "maria - corte (pix)",
                "maria - corte (pix)",
                java.time.Instant.ofEpochMilli(event.getStart().getDateTime().getValue()),
                java.time.Instant.ofEpochMilli(event.getEnd().getDateTime().getValue())
        );
        existingEvent.setClient(client);
        existingEvent.associateServices(List.of(corte));

        when(calendarEventRepository.findWithAssociationsByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(existingEvent));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("corte", corte);
        }});

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(1, result.updated());
        verify(calendarEventRepository).saveAll(argThat(events -> {
            if (events == null) {
                return false;
            }
            List<CalendarEvent> persistedEvents = StreamSupport.stream(events.spliterator(), false).toList();
            return persistedEvents.size() == 1
                    && persistedEvents.get(0).getPaymentType() == PaymentType.PIX;
        }));
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
