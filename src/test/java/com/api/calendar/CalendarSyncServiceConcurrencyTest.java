package com.api.calendar;

import com.api.client.ClientService;
import com.api.google.GoogleCalendarClient;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.user.User;
import com.api.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CalendarSyncServiceConcurrencyTest {

    @Mock private GoogleCalendarClient googleCalendarClient;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private SyncStateRepository syncStateRepository;
    @Mock private CalendarEventServiceMatcher matcher;
    @Mock private ServiceDescriptionNormalizer normalizer;
    @Mock private UserRepository userRepository;
    @Mock private EventTitleParser titleParser;
    @Mock private ClientService clientService;

    private CalendarSyncService syncService;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        syncService = new CalendarSyncService(googleCalendarClient, calendarEventRepository,
                syncStateRepository, matcher, normalizer, userRepository, titleParser, clientService);
        executor = Executors.newFixedThreadPool(2);

        lenient().when(calendarEventRepository.findWithAssociationsByUserIdAndGoogleEventIdIn(anyLong(), anyCollection()))
                .thenReturn(List.of());
        lenient().when(calendarEventRepository.findAllWithAssociationsByUserId(anyLong()))
                .thenReturn(List.of());
        lenient().when(clientService.clientsByNormalizedName(anyLong())).thenReturn(new HashMap<>());
        lenient().when(matcher.servicesByNormalizedDescription(anyLong())).thenReturn(new HashMap<>());
        lenient().when(calendarEventRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(syncStateRepository.save(org.mockito.ArgumentMatchers.any(SyncState.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(normalizer.normalize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(titleParser.parse(anyString()))
                .thenReturn(new EventTitleParser.ParsedTitle(null, List.of(), null));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void shouldSerializeConcurrentSyncForSameUser() throws Exception {
        User user = new User("sub-1", "u1@test.com", "User 1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());

        AtomicInteger inFlight = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        when(googleCalendarClient.fetchEvents(1L, null)).thenAnswer(inv -> {
            int current = inFlight.incrementAndGet();
            maxConcurrent.updateAndGet(existing -> Math.max(existing, current));
            try {
                Thread.sleep(150);
            } finally {
                inFlight.decrementAndGet();
            }
            return new GoogleCalendarClient.CalendarSyncResult(List.of(), "token");
        });

        CountDownLatch startGate = new CountDownLatch(1);
        Future<CalendarSyncService.SyncResult> first = executor.submit(() -> {
            startGate.await(5, TimeUnit.SECONDS);
            return syncService.synchronize(1L);
        });
        Future<CalendarSyncService.SyncResult> second = executor.submit(() -> {
            startGate.await(5, TimeUnit.SECONDS);
            return syncService.synchronize(1L);
        });

        startGate.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);

        assertEquals(1, maxConcurrent.get());
    }

    @Test
    void shouldAllowParallelSyncForDifferentUsers() throws Exception {
        User user1 = new User("sub-1", "u1@test.com", "User 1");
        User user2 = new User("sub-2", "u2@test.com", "User 2");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(syncStateRepository.findByUserId(2L)).thenReturn(Optional.empty());

        AtomicInteger inFlight = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        when(googleCalendarClient.fetchEvents(anyLong(), org.mockito.ArgumentMatchers.isNull())).thenAnswer(inv -> {
            int current = inFlight.incrementAndGet();
            maxConcurrent.updateAndGet(existing -> Math.max(existing, current));
            try {
                Thread.sleep(150);
            } finally {
                inFlight.decrementAndGet();
            }
            return new GoogleCalendarClient.CalendarSyncResult(List.of(), "token");
        });

        CountDownLatch startGate = new CountDownLatch(1);
        Future<CalendarSyncService.SyncResult> first = executor.submit(() -> {
            startGate.await(5, TimeUnit.SECONDS);
            return syncService.synchronize(1L);
        });
        Future<CalendarSyncService.SyncResult> second = executor.submit(() -> {
            startGate.await(5, TimeUnit.SECONDS);
            return syncService.synchronize(2L);
        });

        startGate.countDown();
        first.get(5, TimeUnit.SECONDS);
        second.get(5, TimeUnit.SECONDS);

        assertTrue(maxConcurrent.get() >= 2);
    }
}
