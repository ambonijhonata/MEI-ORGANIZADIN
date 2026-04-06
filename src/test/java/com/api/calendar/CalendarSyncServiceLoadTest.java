package com.api.calendar;

import com.api.client.ClientService;
import com.api.google.GoogleCalendarClient;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.user.User;
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
    @Mock private SyncStateRepository syncStateRepository;
    @Mock private CalendarEventServiceMatcher matcher;
    @Mock private ServiceDescriptionNormalizer normalizer;
    @Mock private UserRepository userRepository;
    @Mock private EventTitleParser titleParser;
    @Mock private ClientService clientService;

    @Test
    void highVolumeSyncShouldCompleteWithStableThroughput() throws Exception {
        CalendarSyncService syncService = new CalendarSyncService(
                googleCalendarClient,
                calendarEventRepository,
                syncStateRepository,
                matcher,
                normalizer,
                userRepository,
                titleParser,
                clientService,
                500,
                false
        );

        User user = new User("sub-load", "load@test.com", "Load User");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(syncStateRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(syncStateRepository.save(org.mockito.ArgumentMatchers.any(SyncState.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        lenient().when(calendarEventRepository.findWithAssociationsByUserIdAndGoogleEventIdIn(anyLong(), anyCollection()))
                .thenReturn(List.of());
        lenient().when(calendarEventRepository.findAllWithAssociationsByUserId(anyLong()))
                .thenReturn(List.of());
        lenient().when(clientService.clientsByNormalizedName(anyLong())).thenReturn(new HashMap<>());
        lenient().when(matcher.servicesByNormalizedDescription(anyLong())).thenReturn(new HashMap<>());
        lenient().when(calendarEventRepository.saveAll(org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(normalizer.normalize(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(titleParser.parse(anyString())).thenReturn(new EventTitleParser.ParsedTitle(null, List.of()));

        List<Event> events = IntStream.range(0, 14_354)
                .mapToObj(i -> new Event()
                        .setId("evt-" + i)
                        .setSummary("cliente-" + i + " - corte")
                        .setStatus("confirmed")
                        .setStart(new EventDateTime().setDateTime(new DateTime(System.currentTimeMillis() + (i * 60_000L))))
                        .setEnd(new EventDateTime().setDateTime(new DateTime(System.currentTimeMillis() + (i * 60_000L) + 3_600_000L))))
                .toList();
        when(googleCalendarClient.fetchEvents(1L, null))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(events, "token-load"));

        long startNs = System.nanoTime();
        syncService.synchronize(1L);
        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;

        System.out.println("high_volume_sync_elapsed_ms=" + elapsedMs + " events=" + events.size());
    }
}
