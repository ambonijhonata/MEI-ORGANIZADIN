package com.api.calendar;

import com.api.client.ClientService;
import com.api.google.GoogleCalendarClient;
import com.api.google.GoogleCalendarTestEvents;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.user.ApplicationUser;
import com.api.user.UserRepository;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@Disabled("Manual performance harness for high-volume sync scenarios")
class CalendarSyncServiceLoadTest {

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

    @Test
    void highVolumeSyncShouldCompleteWithStableThroughput() throws Exception {
        CalendarSyncBatchSettings batchSettings = new CalendarSyncBatchSettings(500, false, 1);
        CalendarSyncAssociationEvaluator associationEvaluator = new CalendarSyncAssociationEvaluator();
        CalendarSyncExistingEventResolver existingEventResolver = new CalendarSyncExistingEventResolver(
                calendarEventRepository,
                calendarEventServiceLinkRepository,
                associationEvaluator
        );
        CalendarSyncMutationPlanner mutationPlanner = new CalendarSyncMutationPlanner(
                clientService,
                matcher,
                normalizer,
                titleParser,
                existingEventResolver,
                associationEvaluator
        );
        CalendarSyncScopeReconciler scopeReconciler = new CalendarSyncScopeReconciler(calendarEventRepository);
        CalendarSyncPersistenceSupport persistenceSupport = new CalendarSyncPersistenceSupport(
                calendarEventRepository,
                calendarEventPaymentRepository,
                calendarEventServiceLinkRepository,
                batchSettings
        );
        CalendarSyncTxSupport txSupport = new CalendarSyncTxSupport();
        CalendarSyncFlowRunner flowRunner = new CalendarSyncFlowRunner(
                syncStateRepository,
                null,
                mutationPlanner,
                scopeReconciler,
                persistenceSupport,
                batchSettings,
                txSupport
        );
        CalendarSyncService syncService = new CalendarSyncService(
                googleCalendarClient,
                syncStateRepository,
                userRepository,
                new UserScopedExecutionLock(),
                flowRunner
        );

        ApplicationUser user = new ApplicationUser("sub-load", "load@test.com", "Load ApplicationUser");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(syncStateRepository.save(org.mockito.ArgumentMatchers.any(SyncState.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        lenient().when(calendarEventRepository.findByUserIdAndGoogleEventIdIn(anyLong(), anyCollection()))
                .thenReturn(List.of());
        lenient().when(calendarEventRepository.findGoogleBackedByUserId(anyLong()))
                .thenReturn(List.of());
        lenient().when(calendarEventRepository.findGoogleBackedByUserId(anyLong()))
                .thenReturn(List.of());
        lenient().when(calendarEventRepository.findGoogleBackedByUserIdAndEventStartGreaterThanEqual(anyLong(), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of());
        lenient().when(clientService.listClientsByNormalizedName(anyLong())).thenReturn(new HashMap<>());
        lenient().when(matcher.servicesByNormalizedDescription(anyLong())).thenReturn(new HashMap<>());
        lenient().when(calendarEventRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(calendarEventServiceLinkRepository.findServiceIdentityRowsByCalendarEventIdIn(anyCollection()))
                .thenReturn(List.of());
        lenient().when(calendarEventRepository.findLegacyServiceIdentityRowsByCalendarEventIdIn(anyCollection()))
                .thenReturn(List.of());
        lenient().when(normalizer.normalize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(titleParser.parse(anyString())).thenReturn(new EventTitleParser.ParsedTitle(null, List.of(), null));

        List<Event> events = IntStream.range(0, 14_354)
                .mapToObj(i -> new Event()
                        .setId("evt-" + i)
                        .setSummary("cliente-" + i + " - corte")
                        .setStatus("confirmed")
                        .setStart(new EventDateTime().setDateTime(new DateTime(System.currentTimeMillis() + (i * 60_000L))))
                        .setEnd(new EventDateTime().setDateTime(new DateTime(System.currentTimeMillis() + (i * 60_000L) + 3_600_000L))))
                .toList();
        when(googleCalendarClient.fetchEvents(1L, null))
                .thenReturn(GoogleCalendarTestEvents.toSyncResult(events, "token-load"));

        long startNs = System.nanoTime();
        syncService.synchronize(1L);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        System.out.println("high_volume_sync_elapsed_ms=" + elapsedMs + " events=" + events.size());
    }
}
