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
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.AbstractList;
import java.util.ArrayList;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalendarSyncServiceTest {

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

    @BeforeEach
    void setUp() {
        syncService = new CalendarSyncService(googleCalendarClient, calendarEventRepository,
                syncStateRepository, matcher, normalizer, userRepository, titleParser, clientService,
                calendarEventPaymentRepository, calendarEventServiceLinkRepository, new UserScopedExecutionLock());

        lenient().when(calendarEventRepository.findByUserIdAndGoogleEventIdIn(anyLong(), anyCollection()))
                .thenReturn(List.of());
        lenient().when(calendarEventRepository.findWithAssociationsByUserIdAndGoogleEventIdIn(anyLong(), anyCollection()))
                .thenReturn(List.of());
        lenient().when(calendarEventRepository.findAllWithAssociationsByUserId(anyLong()))
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
        when(titleParser.parse("maria - corte")).thenReturn(
                new EventTitleParser.ParsedTitle("maria", List.of("corte"), null));

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

        CalendarSyncService.SyncResult result = syncService.synchronize(1L, null);

        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.deleted());
    }

    @Test
    void shouldPreserveExistingSyncTokenWhenIncrementalSyncReturnsNoNextToken() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("old-sync-token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);
        when(googleCalendarClient.fetchEvents(1L, "old-sync-token"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(), null));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.deleted());
        assertEquals("old-sync-token", syncState.getSyncToken());
    }

    @Test
    void shouldPreferIncrementalSyncWhenStartDateAndSyncTokenExist() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("persisted-sync-token");
        LocalDate startDate = LocalDate.of(2026, 4, 1);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event deletedEvent = new Event().setId("deleted-1").setStatus("cancelled");
        CalendarEvent localEvent = new CalendarEvent(user, "deleted-1", "Test", "test",
                java.time.Instant.now(), java.time.Instant.now());
        when(googleCalendarClient.fetchEvents(1L, "persisted-sync-token"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(deletedEvent), "new-token"));
        when(calendarEventRepository.findByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(localEvent));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L, startDate);

        assertEquals(0, result.created());
        assertEquals(1, result.deleted());
        assertEquals("new-token", syncState.getSyncToken());
        verify(googleCalendarClient).fetchEvents(1L, "persisted-sync-token");
        verify(googleCalendarClient, never()).fetchEvents(1L, null, startDate);
        verify(calendarEventRepository).deleteAllInBatch(argThat(iterable ->
                StreamSupport.stream(iterable.spliterator(), false).anyMatch(localEvent::equals)
        ));
    }

    @Test
    void shouldPreserveExistingTokenWhenStartDateSyncRunsIncrementalWithoutNewToken() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("persisted-sync-token");
        LocalDate startDate = LocalDate.of(2026, 4, 1);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);
        when(googleCalendarClient.fetchEvents(1L, "persisted-sync-token"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(), null));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L, startDate);

        assertEquals(0, result.created());
        assertEquals(0, result.updated());
        assertEquals(0, result.deleted());
        assertEquals("persisted-sync-token", syncState.getSyncToken());
        verify(googleCalendarClient).fetchEvents(1L, "persisted-sync-token");
        verify(googleCalendarClient, never()).fetchEvents(1L, null, startDate);
    }

    @Test
    void shouldPersistNextSyncTokenAfterInitialStartDateSync() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        LocalDate startDate = LocalDate.of(2026, 4, 1);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event event = createTestEvent("event-2", "ana - corte");
        when(googleCalendarClient.fetchEvents(1L, null, startDate))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(event), "persisted-token"));
        when(normalizer.normalize(anyString())).thenReturn("ana - corte");
        when(titleParser.parse("ana - corte")).thenReturn(
                new EventTitleParser.ParsedTitle("ana", List.of("corte"), null));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L, startDate);

        assertEquals(1, result.created());
        assertEquals("persisted-token", syncState.getSyncToken());
        verify(googleCalendarClient).fetchEvents(1L, null, startDate);
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
        when(calendarEventRepository.findByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(localEvent));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(1, result.deleted());
        verify(calendarEventRepository).deleteAllInBatch(argThat(iterable ->
                StreamSupport.stream(iterable.spliterator(), false).anyMatch(localEvent::equals)
        ));
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
        Event retainedGoogleEvent = createTestEvent("retained-1", "corte");
        CalendarEvent staleLocalEvent = new CalendarEvent(user, "stale-1", "Old", "old",
                java.time.Instant.now(), java.time.Instant.now());
        CalendarEvent retainedLocalEvent = new CalendarEvent(user, "retained-1", "Corte", "corte",
                java.time.Instant.ofEpochMilli(retainedGoogleEvent.getStart().getDateTime().getValue()),
                java.time.Instant.ofEpochMilli(retainedGoogleEvent.getEnd().getDateTime().getValue()));
        when(googleCalendarClient.fetchEvents(eq(1L), isNull()))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(retainedGoogleEvent), "new-token"));
        when(calendarEventRepository.findGoogleBackedByUserId(1L))
                .thenReturn(List.of(staleLocalEvent, retainedLocalEvent));
        when(calendarEventRepository.findGoogleBackedByUserId(1L))
                .thenReturn(List.of(staleLocalEvent, retainedLocalEvent));
        when(normalizer.normalize("corte")).thenReturn("corte");
        when(titleParser.parse("corte")).thenReturn(new EventTitleParser.ParsedTitle(null, List.of("corte"), null));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertNotNull(result);
        assertEquals(1, result.deleted());
        assertEquals("new-token", syncState.getSyncToken());
        verify(calendarEventRepository).deleteAllInBatch(argThat(iterable -> {
            List<CalendarEvent> deletions = StreamSupport.stream(iterable.spliterator(), false).toList();
            return deletions.size() == 1 && deletions.contains(staleLocalEvent) && !deletions.contains(retainedLocalEvent);
        }));
    }

    @Test
    void shouldNotRestoreExpiredTokenWhenFullResyncReturnsNoNextToken() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("expired-token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);
        when(googleCalendarClient.fetchEvents(1L, "expired-token"))
                .thenThrow(new GoogleCalendarClient.SyncTokenExpiredException("410 Gone"));
        when(googleCalendarClient.fetchEvents(eq(1L), isNull()))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(), null));
        when(calendarEventRepository.findGoogleBackedByUserId(1L)).thenReturn(List.of());

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertNotNull(result);
        assertNull(syncState.getSyncToken());
        verify(googleCalendarClient).fetchEvents(1L, "expired-token");
        verify(googleCalendarClient).fetchEvents(1L, null);
    }

    @Test
    void shouldReconcileStartDateScopeDeletingOnlyMissingEventsOnOrAfterStartDate() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        LocalDate startDate = LocalDate.of(2026, 4, 1);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event retainedGoogleEvent = createTestEvent("keep-1", "corte");
        when(googleCalendarClient.fetchEvents(1L, null, startDate))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(retainedGoogleEvent), "start-scope-token"));
        when(normalizer.normalize("corte")).thenReturn("corte");
        when(titleParser.parse("corte")).thenReturn(new EventTitleParser.ParsedTitle(null, List.of("corte"), null));

        Instant startScope = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        CalendarEvent staleAfterStartDate = new CalendarEvent(
                user, "stale-after", "Stale after", "stale after", startScope.plusSeconds(3600), startScope.plusSeconds(7200)
        );
        CalendarEvent staleBeforeStartDate = new CalendarEvent(
                user, "stale-before", "Stale before", "stale before", startScope.minusSeconds(3600), startScope
        );
        CalendarEvent retainedLocalEvent = new CalendarEvent(
                user, "keep-1", "corte", "corte",
                Instant.ofEpochMilli(retainedGoogleEvent.getStart().getDateTime().getValue()),
                Instant.ofEpochMilli(retainedGoogleEvent.getEnd().getDateTime().getValue())
        );

        when(calendarEventRepository.findByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(retainedLocalEvent));
        when(calendarEventRepository.findGoogleBackedByUserIdAndEventStartGreaterThanEqual(1L, startScope))
                .thenReturn(List.of(staleAfterStartDate, retainedLocalEvent));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L, startDate);

        assertEquals(1, result.deleted());
        verify(calendarEventRepository).deleteAllInBatch(argThat(iterable -> {
            List<CalendarEvent> deletions = StreamSupport.stream(iterable.spliterator(), false).toList();
            return deletions.size() == 1
                    && deletions.contains(staleAfterStartDate)
                    && !deletions.contains(retainedLocalEvent)
                    && !deletions.contains(staleBeforeStartDate);
        }));
    }

    @Test
    void shouldRepairStaleGoogleBackedRowsOnInitialFullSync() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event retainedGoogleEvent = createTestEvent("keep-1", "corte");
        when(googleCalendarClient.fetchEvents(1L, null))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(retainedGoogleEvent), "initial-token"));
        when(normalizer.normalize("corte")).thenReturn("corte");
        when(titleParser.parse("corte")).thenReturn(new EventTitleParser.ParsedTitle(null, List.of("corte"), null));

        CalendarEvent staleLocalEvent = new CalendarEvent(user, "stale-1", "Old", "old",
                Instant.now(), Instant.now());
        CalendarEvent retainedLocalEvent = new CalendarEvent(user, "keep-1", "corte", "corte",
                Instant.ofEpochMilli(retainedGoogleEvent.getStart().getDateTime().getValue()),
                Instant.ofEpochMilli(retainedGoogleEvent.getEnd().getDateTime().getValue()));

        when(calendarEventRepository.findGoogleBackedByUserId(1L))
                .thenReturn(List.of(staleLocalEvent, retainedLocalEvent));
        when(calendarEventRepository.findGoogleBackedByUserId(1L))
                .thenReturn(List.of(staleLocalEvent, retainedLocalEvent));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(1, result.deleted());
        assertEquals("initial-token", syncState.getSyncToken());
        verify(calendarEventRepository).deleteAllInBatch(argThat(iterable -> {
            List<CalendarEvent> deletions = StreamSupport.stream(iterable.spliterator(), false).toList();
            return deletions.size() == 1
                    && deletions.contains(staleLocalEvent)
                    && !deletions.contains(retainedLocalEvent);
        }));
    }

    @Test
    void shouldDeletePaymentsBeforeDeletingAppointments() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("sync-token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event deletedEvent = new Event().setId("deleted-1").setStatus("cancelled");
        CalendarEvent localEvent = new CalendarEvent(user, "deleted-1", "Test", "test",
                Instant.now(), Instant.now());
        setCalendarEventId(localEvent, 101L);

        when(googleCalendarClient.fetchEvents(1L, "sync-token"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(deletedEvent), "new-token"));
        when(calendarEventRepository.findByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(localEvent));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(1, result.deleted());
        var inOrder = inOrder(calendarEventPaymentRepository, calendarEventRepository);
        inOrder.verify(calendarEventPaymentRepository)
                .deleteInBulkByCalendarEventIdIn(argThat(ids -> ids != null && ids.size() == 1 && ids.contains(101L)));
        inOrder.verify(calendarEventPaymentRepository).flush();
        inOrder.verify(calendarEventRepository).deleteAllInBatch(argThat(iterable ->
                StreamSupport.stream(iterable.spliterator(), false).anyMatch(localEvent::equals)
        ));
    }

    @Test
    void shouldCommitDeletionBatchWithMixedPaymentPresence() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("sync-token");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        Event deletedWithPayment = new Event().setId("deleted-with-payment").setStatus("cancelled");
        Event deletedWithoutPayment = new Event().setId("deleted-without-payment").setStatus("cancelled");

        CalendarEvent localWithPayment = new CalendarEvent(
                user, "deleted-with-payment", "With payment", "with payment", Instant.now(), Instant.now()
        );
        CalendarEvent localWithoutPayment = new CalendarEvent(
                user, "deleted-without-payment", "Without payment", "without payment", Instant.now(), Instant.now()
        );
        setCalendarEventId(localWithPayment, 201L);
        setCalendarEventId(localWithoutPayment, 202L);

        when(googleCalendarClient.fetchEvents(1L, "sync-token"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(
                        List.of(deletedWithPayment, deletedWithoutPayment),
                        "new-token"
                ));
        when(calendarEventRepository.findByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(localWithPayment, localWithoutPayment));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(2, result.deleted());
        verify(calendarEventPaymentRepository)
                .deleteInBulkByCalendarEventIdIn(argThat(ids ->
                        ids != null && ids.size() == 2 && ids.contains(201L) && ids.contains(202L)
                ));
        verify(calendarEventRepository).deleteAllInBatch(argThat(iterable -> {
            List<CalendarEvent> deletions = StreamSupport.stream(iterable.spliterator(), false).toList();
            return deletions.size() == 2
                    && deletions.contains(localWithPayment)
                    && deletions.contains(localWithoutPayment);
        }));
    }

    @Test
    void shouldIgnoreLocalRowsWithoutGoogleEventIdDuringReconciliation() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);

        when(googleCalendarClient.fetchEvents(1L, null))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(), "token"));
        when(calendarEventRepository.findGoogleBackedByUserId(1L))
                .thenReturn(List.of());

        CalendarEvent withoutGoogleEventId = new CalendarEvent(user, null, "Manual", "manual", Instant.now(), Instant.now());
        CalendarEvent staleLocalEvent = new CalendarEvent(user, "stale-1", "Old", "old", Instant.now(), Instant.now());
        when(calendarEventRepository.findGoogleBackedByUserId(1L))
                .thenReturn(List.of(withoutGoogleEventId, staleLocalEvent));

        CalendarSyncService.SyncResult result = syncService.synchronize(1L);

        assertEquals(1, result.deleted());
        verify(calendarEventRepository).deleteAllInBatch(argThat(iterable -> {
            List<CalendarEvent> deletions = StreamSupport.stream(iterable.spliterator(), false).toList();
            return deletions.size() == 1
                    && deletions.contains(staleLocalEvent)
                    && !deletions.contains(withoutGoogleEventId);
        }));
    }

    @Test
    void shouldAvoidDereferencingLegacyServiceProxyDuringServiceComparison() throws IOException {
        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("sync-token");

        Instant start = Instant.now();
        Instant end = start.plusSeconds(1800);
        Service matchedService = new Service(user, "Corte", "corte", new BigDecimal("50.00"));
        CalendarEvent existingEvent = new CalendarEvent(user, "event-1", "maria - corte", "maria - corte", start, end);
        existingEvent.associateServices(List.of(matchedService));
        setCalendarEventId(existingEvent, 901L);
        CalendarEvent existingSpy = spy(existingEvent);
        lenient().doThrow(new IllegalStateException("Legacy lazy proxy should not be touched"))
                .when(existingSpy).getService();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);
        when(calendarEventRepository.findWithAssociationsByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(existingSpy));
        when(matcher.servicesByNormalizedDescription(1L))
                .thenReturn(new HashMap<>() {{
                    put("corte", matchedService);
                }});
        lenient().when(normalizer.normalize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(titleParser.parse("maria - corte"))
                .thenReturn(new EventTitleParser.ParsedTitle("maria", List.of("corte"), null));

        Event incoming = createTestEvent("event-1", "maria - corte");
        incoming.setStart(new EventDateTime().setDateTime(new DateTime(start.toEpochMilli())));
        incoming.setEnd(new EventDateTime().setDateTime(new DateTime(end.toEpochMilli())));
        when(googleCalendarClient.fetchEvents(1L, "sync-token"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(incoming), "next-token"));

        assertDoesNotThrow(() -> syncService.synchronize(1L));
        verify(existingSpy, never()).getService();
    }

    @Test
    void shouldUpdateLazyServiceLinksInsideActiveTransaction() throws Exception {
        syncService.configureTransactionTemplate(new AbstractPlatformTransactionManager() {
            @Override
            protected Object doGetTransaction() {
                return new Object();
            }

            @Override
            protected void doBegin(Object transaction, TransactionDefinition definition) {
            }

            @Override
            protected void doCommit(DefaultTransactionStatus status) {
            }

            @Override
            protected void doRollback(DefaultTransactionStatus status) {
            }
        });

        User user = new User("sub", "email@test.com", "Name");
        SyncState syncState = new SyncState(user);
        syncState.markSynced("sync-token");

        Instant start = Instant.now();
        Instant end = start.plusSeconds(1800);
        Service oldService = new Service(user, "Corte", "corte", new BigDecimal("50.00"));
        Service newService = new Service(user, "Barba", "barba", new BigDecimal("30.00"));
        CalendarEvent existingEvent = new CalendarEvent(user, "event-1", "maria - corte", "maria - corte", start, end);
        existingEvent.associateServices(List.of(oldService));
        setCalendarEventId(existingEvent, 902L);
        LazyAwareServiceLinkList lazyServiceLinks = new LazyAwareServiceLinkList(new ArrayList<>(existingEvent.getServiceLinks()));
        setServiceLinks(existingEvent, lazyServiceLinks);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any(SyncState.class))).thenReturn(syncState);
        when(calendarEventRepository.findWithAssociationsByUserIdAndGoogleEventIdIn(eq(1L), anyCollection()))
                .thenReturn(List.of(existingEvent));
        when(matcher.servicesByNormalizedDescription(1L))
                .thenReturn(new HashMap<>() {{
                    put("barba", newService);
                }});
        lenient().when(normalizer.normalize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(titleParser.parse("maria - barba"))
                .thenReturn(new EventTitleParser.ParsedTitle("maria", List.of("barba"), null));

        Event incoming = createTestEvent("event-1", "maria - barba");
        incoming.setStart(new EventDateTime().setDateTime(new DateTime(start.toEpochMilli())));
        incoming.setEnd(new EventDateTime().setDateTime(new DateTime(end.toEpochMilli())));
        when(googleCalendarClient.fetchEvents(1L, "sync-token"))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(incoming), "next-token"));

        CalendarSyncService.SyncResult result = assertDoesNotThrow(() -> syncService.synchronize(1L));

        assertEquals(1, result.updated());
        assertEquals("Barba", existingEvent.getServiceDescriptionSnapshot());
        assertEquals(1, lazyServiceLinks.rawSize());
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

    private void setServiceLinks(CalendarEvent event, List<CalendarEventServiceLink> serviceLinks) {
        try {
            var field = CalendarEvent.class.getDeclaredField("serviceLinks");
            field.setAccessible(true);
            field.set(event, serviceLinks);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to override CalendarEvent serviceLinks for test setup", e);
        }
    }

    private static final class LazyAwareServiceLinkList extends AbstractList<CalendarEventServiceLink> {
        private final List<CalendarEventServiceLink> delegate;

        private LazyAwareServiceLinkList(List<CalendarEventServiceLink> delegate) {
            this.delegate = delegate;
        }

        @Override
        public CalendarEventServiceLink get(int index) {
            ensureTransactionActive();
            return delegate.get(index);
        }

        @Override
        public int size() {
            ensureTransactionActive();
            return delegate.size();
        }

        @Override
        public void clear() {
            ensureTransactionActive();
            delegate.clear();
        }

        @Override
        public void add(int index, CalendarEventServiceLink element) {
            ensureTransactionActive();
            delegate.add(index, element);
        }

        @Override
        public CalendarEventServiceLink set(int index, CalendarEventServiceLink element) {
            ensureTransactionActive();
            return delegate.set(index, element);
        }

        @Override
        public CalendarEventServiceLink remove(int index) {
            ensureTransactionActive();
            return delegate.remove(index);
        }

        private void ensureTransactionActive() {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new LazyInitializationException("Service links accessed outside transaction");
            }
        }

        private int rawSize() {
            return delegate.size();
        }
    }
}
