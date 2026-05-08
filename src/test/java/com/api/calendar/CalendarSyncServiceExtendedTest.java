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
    @Mock private CalendarEventServiceLinkRepository calendarEventServiceLinkRepository;
    @Mock private SyncStateRepository syncStateRepository;
    @Mock private CalendarEventServiceMatcher matcher;
    @Mock private ServiceDescriptionNormalizer normalizer;
    @Mock private UserRepository userRepository;
    @Mock private EventTitleParser titleParser;
    @Mock private ClientService clientService;

    private CalendarSyncService syncService;
    private CalendarEventReprocessor reprocessor;

    @BeforeEach
    void setUp() {
        UserScopedExecutionLock userScopedExecutionLock = new UserScopedExecutionLock();
        reprocessor = new CalendarEventReprocessor(
                calendarEventRepository,
                calendarEventServiceLinkRepository,
                matcher,
                titleParser,
                normalizer,
                syncStateRepository,
                userScopedExecutionLock
        );
        syncService = new CalendarSyncService(googleCalendarClient, calendarEventRepository,
                syncStateRepository, matcher, normalizer, userRepository, titleParser, clientService,
                reprocessor, calendarEventPaymentRepository, calendarEventServiceLinkRepository, userScopedExecutionLock);

        lenient().when(calendarEventRepository.findByUserIdAndGoogleEventIdIn(anyLong(), anyCollection()))
                .thenReturn(List.of());
        lenient().when(calendarEventRepository.findGoogleBackedByUserId(anyLong()))
                .thenReturn(List.of());
        lenient().when(calendarEventRepository.findGoogleBackedByUserId(anyLong()))
                .thenReturn(List.of());
        lenient().when(calendarEventRepository.findGoogleBackedByUserIdAndEventStartGreaterThanEqual(anyLong(), any(Instant.class)))
                .thenReturn(List.of());
        lenient().when(clientService.clientsByNormalizedName(anyLong())).thenReturn(new HashMap<>());
        lenient().when(matcher.servicesByNormalizedDescription(anyLong())).thenReturn(new HashMap<>());
        lenient().when(calendarEventRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(calendarEventServiceLinkRepository.findServiceIdentityRowsByCalendarEventIdIn(anyCollection()))
                .thenReturn(List.of());
        lenient().when(calendarEventRepository.findLegacyServiceIdentityRowsByCalendarEventIdIn(anyCollection()))
                .thenReturn(List.of());
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
    void shouldMarkFailedOnRuntimeException() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);
        when(googleCalendarClient.fetchEvents(eq(1L), isNull()))
                .thenThrow(new RuntimeException("Unexpected processing failure"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> syncService.synchronize(1L));
        assertEquals("Unexpected processing failure", ex.getMessage());
        assertEquals(SyncStatus.SYNC_FAILED, syncState.getStatus());
        assertEquals("INTERNAL_SYNC_ERROR", syncState.getErrorCategory());
        assertEquals("Unexpected processing failure", syncState.getErrorMessage());
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
        when(calendarEventRepository.findByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
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

        when(calendarEventRepository.findByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(existingEvent));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(0, result.created());
        assertEquals(0, result.updated());
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

        when(calendarEventRepository.findByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(existingEvent));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("corte", corte);
            put("barba", barba);
        }});

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(0, result.updated());
        verify(calendarEventRepository, never()).saveAll(anyList());
        verify(calendarEventRepository, never()).flush();
        verify(calendarEventServiceLinkRepository, never()).deleteInBulkByCalendarEventIdIn(anyCollection());
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

        when(calendarEventRepository.findByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
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

    @Test
    void shouldReplacePersistedLinksBeforeSavingChangedAssociations() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("sync-token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event event = createTestEvent("e1", "maria - barba");
        when(googleCalendarClient.fetchEvents(1L, "sync-token"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "next-token"));
        when(normalizer.normalize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(titleParser.parse("maria - barba"))
                .thenReturn(new EventTitleParser.ParsedTitle("maria", List.of("barba"), null));

        Client maria = new Client(user, "Maria", "maria");
        when(clientService.findOrCreateByName(1L, user, "maria")).thenReturn(maria);

        Service corte = new Service(user, "Corte", "corte", new BigDecimal("50.00"));
        Service barba = new Service(user, "Barba", "barba", new BigDecimal("30.00"));
        CalendarEvent existingEvent = new CalendarEvent(
                user,
                "e1",
                "maria - corte",
                "maria - corte",
                java.time.Instant.ofEpochMilli(event.getStart().getDateTime().getValue()),
                java.time.Instant.ofEpochMilli(event.getEnd().getDateTime().getValue())
        );
        existingEvent.setClient(maria);
        existingEvent.associateServices(List.of(corte));
        setCalendarEventId(existingEvent, 401L);

        when(calendarEventRepository.findWithAssociationsByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(existingEvent));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("barba", barba);
        }});

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(1, result.updated());
        var inOrder = inOrder(calendarEventServiceLinkRepository, calendarEventRepository);
        inOrder.verify(calendarEventServiceLinkRepository)
                .deleteInBulkByCalendarEventIdIn(argThat(ids -> ids != null && ids.size() == 1 && ids.contains(401L)));
        inOrder.verify(calendarEventServiceLinkRepository).flush();
        inOrder.verify(calendarEventRepository).saveAll(anyList());
        assertEquals(1, existingEvent.getServiceLinks().size());
        assertEquals("Barba", existingEvent.getServiceDescriptionSnapshot());
        assertEquals(new BigDecimal("30.00"), existingEvent.getServiceValueSnapshot());
    }

    @Test
    void shouldReplaceServiceLinksBeforeDeletionCleanupWhenChunkMixesUpdatedAndDeletedEvents() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("sync-token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event updatedGoogleEvent = createTestEvent("keep-1", "maria - barba");
        Event deletedGoogleEvent = new Event().setId("deleted-1").setStatus("cancelled");
        when(googleCalendarClient.fetchEvents(1L, "sync-token"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(
                        List.of(updatedGoogleEvent, deletedGoogleEvent),
                        "next-token"
                ));
        when(normalizer.normalize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(titleParser.parse("maria - barba"))
                .thenReturn(new EventTitleParser.ParsedTitle("maria", List.of("barba"), null));

        Client maria = new Client(user, "Maria", "maria");
        when(clientService.findOrCreateByName(1L, user, "maria")).thenReturn(maria);

        Service corte = new Service(user, "Corte", "corte", new BigDecimal("50.00"));
        Service barba = new Service(user, "Barba", "barba", new BigDecimal("30.00"));
        CalendarEvent existingUpdatedEvent = new CalendarEvent(
                user,
                "keep-1",
                "maria - corte",
                "maria - corte",
                Instant.ofEpochMilli(updatedGoogleEvent.getStart().getDateTime().getValue()),
                Instant.ofEpochMilli(updatedGoogleEvent.getEnd().getDateTime().getValue())
        );
        existingUpdatedEvent.setClient(maria);
        existingUpdatedEvent.associateServices(List.of(corte));
        setCalendarEventId(existingUpdatedEvent, 701L);

        CalendarEvent existingDeletedEvent = new CalendarEvent(
                user,
                "deleted-1",
                "to delete",
                "to delete",
                Instant.now(),
                Instant.now()
        );
        setCalendarEventId(existingDeletedEvent, 702L);

        when(calendarEventRepository.findWithAssociationsByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(existingUpdatedEvent, existingDeletedEvent));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("barba", barba);
        }});

        CalendarSyncService.SyncResult result = assertDoesNotThrow(() -> syncService.synchronize(1L));

        assertEquals(1, result.updated());
        assertEquals(1, result.deleted());

        var inOrder = inOrder(calendarEventServiceLinkRepository, calendarEventPaymentRepository, calendarEventRepository);
        inOrder.verify(calendarEventServiceLinkRepository)
                .deleteInBulkByCalendarEventIdIn(argThat(ids -> ids != null && ids.size() == 1 && ids.contains(701L)));
        inOrder.verify(calendarEventServiceLinkRepository).flush();
        inOrder.verify(calendarEventPaymentRepository)
                .deleteInBulkByCalendarEventIdIn(argThat(ids -> ids != null && ids.size() == 1 && ids.contains(702L)));
        inOrder.verify(calendarEventPaymentRepository).flush();
        inOrder.verify(calendarEventRepository).deleteAllInBatch(argThat(iterable -> {
            List<CalendarEvent> deletions = StreamSupport.stream(iterable.spliterator(), false).toList();
            return deletions.size() == 1 && deletions.contains(existingDeletedEvent);
        }));
        inOrder.verify(calendarEventRepository).saveAll(anyList());

        assertEquals(1, existingUpdatedEvent.getServiceLinks().size());
        assertEquals("Barba", existingUpdatedEvent.getServiceDescriptionSnapshot());
        assertEquals(new BigDecimal("30.00"), existingUpdatedEvent.getServiceValueSnapshot());
    }

    @Test
    void shouldKeepCanonicalLinksAfterInitialSyncReprocessingAndFollowupSync() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        Service sobrancelha = new Service(user, "Sobrancelha", "sobrancelha", new BigDecimal("48.00"));
        CalendarEvent[] persistedHolder = new CalendarEvent[1];

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);
        when(calendarEventRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<CalendarEvent> events = inv.getArgument(0);
            for (CalendarEvent persisted : events) {
                if (persisted.getId() == null) {
                    setCalendarEventId(persisted, 501L);
                }
                persistedHolder[0] = persisted;
            }
            return events;
        });

        Event event = createTestEvent("e1", "rodrigo - sobrancelha");
        when(googleCalendarClient.fetchEvents(1L, null))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "token-1"));
        when(googleCalendarClient.fetchEvents(1L, "token-1"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "token-2"));
        when(normalizer.normalize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(titleParser.parse("rodrigo - sobrancelha"))
                .thenReturn(new EventTitleParser.ParsedTitle("rodrigo", List.of("sobrancelha"), null));

        Client rodrigo = new Client(user, "Rodrigo", "rodrigo");
        when(clientService.findOrCreateByName(1L, user, "rodrigo")).thenReturn(rodrigo);
        when(matcher.servicesByNormalizedDescription(1L))
                .thenReturn(new HashMap<>())
                .thenReturn(new HashMap<>() {{
                    put("sobrancelha", sobrancelha);
                }})
                .thenReturn(new HashMap<>() {{
                    put("sobrancelha", sobrancelha);
                }});

        CalendarSyncService.SyncResult initialSync = syncService.synchronize(1L);

        assertEquals(1, initialSync.created());
        assertNotNull(persistedHolder[0]);
        assertFalse(persistedHolder[0].isIdentified());
        assertEquals(0, persistedHolder[0].getServiceLinks().size());

        when(calendarEventRepository.findByUserIdAndIdentifiedFalse(1L)).thenReturn(List.of(persistedHolder[0]));

        reprocessor.reprocessUnidentifiedEvents(1L);

        assertTrue(persistedHolder[0].isIdentified());
        assertEquals(1, persistedHolder[0].getServiceLinks().size());
        assertEquals("Sobrancelha", persistedHolder[0].getServiceDescriptionSnapshot());
        assertEquals(new BigDecimal("48.00"), persistedHolder[0].getServiceValueSnapshot());

        when(calendarEventRepository.findWithAssociationsByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(persistedHolder[0]));

        CalendarSyncService.SyncResult followupSync = syncService.synchronize(1L);

        assertEquals(0, followupSync.updated());
        assertEquals(1, persistedHolder[0].getServiceLinks().size());
        verify(calendarEventServiceLinkRepository, times(1))
                .deleteInBulkByCalendarEventIdIn(argThat(ids -> ids != null && ids.contains(501L)));
    }

    @Test
    void shouldKeepCanonicalLinksAfterLateServiceEnrichmentAndFollowupSync() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        Service sobrancelha = serviceWithId(user, 1L, "Sobrancelha", "sobrancelha", "48.00");
        Service buco = serviceWithId(user, 2L, "Buco", "buco", "23.00");
        Service tintura = serviceWithId(user, 3L, "Tintura", "tintura", "35.00");
        CalendarEvent[] persistedHolder = new CalendarEvent[1];

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);
        when(calendarEventRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<CalendarEvent> events = inv.getArgument(0);
            for (CalendarEvent persisted : events) {
                if (persisted.getId() == null) {
                    setCalendarEventId(persisted, 601L);
                }
                persistedHolder[0] = persisted;
            }
            return events;
        });

        Event event = createTestEvent("e1", "fulano - sobrancelha + buco + tintura");
        when(googleCalendarClient.fetchEvents(1L, null))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "token-1"));
        when(googleCalendarClient.fetchEvents(1L, "token-1"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "token-2"));
        when(normalizer.normalize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(titleParser.parse("fulano - sobrancelha + buco + tintura"))
                .thenReturn(new EventTitleParser.ParsedTitle("fulano", List.of("sobrancelha", "buco", "tintura"), null));

        Client fulano = new Client(user, "Fulano", "fulano");
        when(clientService.findOrCreateByName(1L, user, "fulano")).thenReturn(fulano);
        when(matcher.servicesByNormalizedDescription(1L))
                .thenReturn(new HashMap<>() {{
                    put("sobrancelha", sobrancelha);
                }})
                .thenReturn(new HashMap<>() {{
                    put("sobrancelha", sobrancelha);
                    put("buco", buco);
                    put("tintura", tintura);
                }})
                .thenReturn(new HashMap<>() {{
                    put("sobrancelha", sobrancelha);
                    put("buco", buco);
                    put("tintura", tintura);
                }});

        CalendarSyncService.SyncResult initialSync = syncService.synchronize(1L);

        assertEquals(1, initialSync.created());
        assertNotNull(persistedHolder[0]);
        assertTrue(persistedHolder[0].isIdentified());
        assertEquals(1, persistedHolder[0].getServiceLinks().size());
        assertEquals(new BigDecimal("48.00"), persistedHolder[0].getServiceValueSnapshot());

        when(calendarEventRepository.findAllWithAssociationsByUserId(1L)).thenReturn(List.of(persistedHolder[0]));

        reprocessor.enrichSynchronizedAppointments(1L);

        assertEquals(3, persistedHolder[0].getServiceLinks().size());
        assertEquals("Sobrancelha", persistedHolder[0].getServiceDescriptionSnapshot());
        assertEquals(new BigDecimal("106.00"), persistedHolder[0].getServiceValueSnapshot());

        when(calendarEventRepository.findWithAssociationsByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(persistedHolder[0]));

        CalendarSyncService.SyncResult followupSync = syncService.synchronize(1L);

        assertEquals(0, followupSync.updated());
        assertEquals(3, persistedHolder[0].getServiceLinks().size());
        verify(calendarEventServiceLinkRepository, never()).deleteInBulkByCalendarEventIdIn(argThat(ids -> ids != null && ids.contains(601L)));
    }

    @Test
    void shouldConsumePendingCatalogEnrichmentDuringSyncWithoutGoogleDelta() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.requestCatalogEnrichment();
        Service sobrancelha = serviceWithId(user, 1L, "Sobrancelha", "sobrancelha", "48.00");
        Service buco = serviceWithId(user, 2L, "Buco", "buco", "23.00");

        CalendarEvent existingEvent = new CalendarEvent(
                user,
                "e1",
                "sofia - sobrancelha + buco",
                "sofia - sobrancelha + buco",
                Instant.now(),
                Instant.now()
        );
        existingEvent.associateServices(List.of(sobrancelha));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);
        when(googleCalendarClient.fetchEvents(1L, null))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(), "token-1"));
        when(calendarEventRepository.findAllWithAssociationsByUserId(1L)).thenReturn(List.of(existingEvent));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("sobrancelha", sobrancelha);
            put("buco", buco);
        }});
        when(titleParser.parse("sofia - sobrancelha + buco"))
                .thenReturn(new EventTitleParser.ParsedTitle("sofia", List.of("sobrancelha", "buco"), null));
        when(normalizer.normalize("sobrancelha")).thenReturn("sobrancelha");
        when(normalizer.normalize("buco")).thenReturn("buco");

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(2, existingEvent.getServiceLinks().size());
        assertFalse(syncState.hasPendingCatalogEnrichment());
        verify(calendarEventRepository).saveAll(argThat(events -> {
            if (events == null) {
                return false;
            }
            List<CalendarEvent> persistedEvents = StreamSupport.stream(events.spliterator(), false).toList();
            return persistedEvents.size() == 1 && persistedEvents.contains(existingEvent);
        }));
    }

    @Test
    void shouldRecoverLateServiceEnrichmentOnSyncAfterAsyncAttemptDidNotRun() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.requestCatalogEnrichment();
        Service sobrancelha = serviceWithId(user, 1L, "Sobrancelha", "sobrancelha", "48.00");
        Service tintura = serviceWithId(user, 2L, "Tintura", "tintura", "25.00");

        CalendarEvent existingEvent = new CalendarEvent(
                user,
                "e2",
                "talita - sobrancelha + tintura",
                "talita - sobrancelha + tintura",
                Instant.now(),
                Instant.now()
        );
        existingEvent.associateServices(List.of(sobrancelha));

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);
        when(googleCalendarClient.fetchEvents(1L, null))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(), "token-1"));
        when(calendarEventRepository.findAllWithAssociationsByUserId(1L)).thenReturn(List.of(existingEvent));
        when(matcher.servicesByNormalizedDescription(1L)).thenReturn(new HashMap<>() {{
            put("sobrancelha", sobrancelha);
            put("tintura", tintura);
        }});
        when(titleParser.parse("talita - sobrancelha + tintura"))
                .thenReturn(new EventTitleParser.ParsedTitle("talita", List.of("sobrancelha", "tintura"), null));
        when(normalizer.normalize("sobrancelha")).thenReturn("sobrancelha");
        when(normalizer.normalize("tintura")).thenReturn("tintura");

        syncService.synchronize(1L);

        assertEquals(2, existingEvent.getServiceLinks().size());
        assertFalse(syncState.hasPendingCatalogEnrichment());
        assertEquals(new BigDecimal("73.00"), existingEvent.getServiceValueSnapshot());
    }

    private Service serviceWithId(User user, Long id, String description, String normalized, String value) {
        Service service = new Service(user, description, normalized, new BigDecimal(value));
        try {
            var idField = Service.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(service, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to set Service id for test setup", e);
        }
        return service;
    }

    private Event createTestEvent(String id, String summary) {
        return new Event()
                .setId(id)
                .setSummary(summary)
                .setStatus("confirmed")
                .setStart(new EventDateTime().setDateTime(new DateTime(System.currentTimeMillis())))
                .setEnd(new EventDateTime().setDateTime(new DateTime(System.currentTimeMillis() + 3600000)));
    }

    private void setCalendarEventId(CalendarEvent event, Long eventId) {
        try {
            var idField = CalendarEvent.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(event, eventId);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to set CalendarEvent id for test setup", e);
        }
    }
}
