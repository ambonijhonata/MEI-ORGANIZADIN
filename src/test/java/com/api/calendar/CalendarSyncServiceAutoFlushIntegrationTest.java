package com.api.calendar;

import com.api.MeiOrganizadinApplication;
import com.api.client.ClientRepository;
import com.api.client.ClientService;
import com.api.google.GoogleCalendarClient;
import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.servicecatalog.ServiceRepository;
import com.api.user.User;
import com.api.user.UserRepository;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.jdbc.batch_size=0",
        "spring.datasource.url=jdbc:h2:mem:calendar-sync-autoflush;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false;NON_KEYWORDS=VALUE"
})
@ContextConfiguration(classes = MeiOrganizadinApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CalendarSyncServiceAutoFlushIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private CalendarEventRepository calendarEventRepository;

    @Autowired
    private CalendarEventPaymentRepository calendarEventPaymentRepository;

    @Autowired
    private CalendarEventServiceLinkRepository calendarEventServiceLinkRepository;

    @Autowired
    private SyncStateRepository syncStateRepository;

    @Autowired
    private PlatformTransactionManager platformTransactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private GoogleCalendarClient googleCalendarClient;
    private CalendarEventServiceMatcher matcher;
    private CalendarSyncService syncService;
    private ServiceDescriptionNormalizer normalizer;
    private EventTitleParser titleParser;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_calendar_event_services_event_service_occurrence
                ON calendar_event_services (calendar_event_id, service_id, occurrence_index)
                """);

        googleCalendarClient = Mockito.mock(GoogleCalendarClient.class);
        matcher = Mockito.mock(CalendarEventServiceMatcher.class);
        normalizer = new ServiceDescriptionNormalizer();
        titleParser = new EventTitleParser();

        ClientService clientService = new ClientService(
                clientRepository,
                userRepository,
                calendarEventRepository,
                normalizer
        );

        syncService = new CalendarSyncService(
                googleCalendarClient,
                calendarEventRepository,
                syncStateRepository,
                matcher,
                normalizer,
                userRepository,
                titleParser,
                clientService,
                null,
                calendarEventPaymentRepository,
                calendarEventServiceLinkRepository,
                new UserScopedExecutionLock()
        );
        syncService.configureTransactionTemplate(platformTransactionManager);
    }

    @Test
    void shouldAvoidDuplicateKeyAutoFlushWhenLaterClientLookupRunsAfterServiceReplacement() throws Exception {
        User user = userRepository.saveAndFlush(new User("sub-sync", "sync@test.com", "Sync Test"));
        Service corte = serviceRepository.saveAndFlush(new Service(user, "Corte", "corte", new BigDecimal("50.00")));
        Service barba = serviceRepository.saveAndFlush(new Service(user, "Barba", "barba", new BigDecimal("30.00")));

        CalendarEvent existingEvent = new CalendarEvent(
                user,
                "google-event-1",
                "corte",
                "corte",
                Instant.parse("2026-05-08T12:00:00Z"),
                Instant.parse("2026-05-08T13:00:00Z")
        );
        existingEvent.associateServices(List.of(corte));
        calendarEventRepository.saveAndFlush(existingEvent);
        entityManager.clear();

        when(googleCalendarClient.fetchEvents(eq(user.getId()), isNull()))
                .thenReturn(new GoogleCalendarClient.CalendarSyncResult(List.of(
                        timedEvent("google-event-1", "barba", "2026-05-08T12:00:00Z", "2026-05-08T13:00:00Z"),
                        timedEvent("google-event-2", "maria - barba", "2026-05-08T14:00:00Z", "2026-05-08T15:00:00Z")
                ), "token-sync"));

        when(matcher.servicesByNormalizedDescription(user.getId())).thenReturn(new HashMap<>() {{
            put("corte", corte);
            put("barba", barba);
        }});

        CalendarSyncService.SyncResult result = assertDoesNotThrow(() -> syncService.synchronize(user.getId()));

        assertEquals(1, result.created());
        assertEquals(1, result.updated());

        entityManager.clear();

        CalendarEvent reloaded = calendarEventRepository
                .findWithAssociationsByUserIdAndGoogleEventIdIn(user.getId(), List.of("google-event-1"))
                .stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected updated event to exist"));

        assertEquals("Barba", reloaded.getServiceDescriptionSnapshot());
        assertEquals(1, reloaded.getServiceLinks().size());
        assertEquals("Barba", reloaded.getServiceLinks().get(0).getServiceDescriptionSnapshot());
        assertEquals(1, calendarEventServiceLinkRepository.findByCalendarEventId(reloaded.getId()).size());
        assertEquals(Optional.of("maria"), clientRepository.findByUserIdAndNormalizedName(user.getId(), "maria").map(client -> client.getName()));
    }

    private Event timedEvent(String id, String summary, String startIso, String endIso) {
        return new Event()
                .setId(id)
                .setSummary(summary)
                .setStatus("confirmed")
                .setStart(new EventDateTime().setDateTime(new DateTime(Instant.parse(startIso).toEpochMilli())))
                .setEnd(new EventDateTime().setDateTime(new DateTime(Instant.parse(endIso).toEpochMilli())));
    }
}
