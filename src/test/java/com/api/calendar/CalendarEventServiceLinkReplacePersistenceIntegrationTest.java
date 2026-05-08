package com.api.calendar;

import com.api.MeiOrganizadinApplication;
import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceRepository;
import com.api.user.User;
import com.api.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.jdbc.batch_size=0",
        "spring.datasource.url=jdbc:h2:mem:service-link-replace;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false;NON_KEYWORDS=VALUE"
})
@ContextConfiguration(classes = MeiOrganizadinApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CalendarEventServiceLinkReplacePersistenceIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private CalendarEventRepository calendarEventRepository;

    @Autowired
    private CalendarEventServiceLinkRepository calendarEventServiceLinkRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void ensureUniqueIndexPresent() {
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_calendar_event_services_event_service
                ON calendar_event_services (calendar_event_id, service_id)
                """);
    }

    @Test
    void shouldReplacePersistedServiceLinksWithoutDuplicateKeyOrStaleDelete() {
        User user = userRepository.saveAndFlush(new User("sub-replace", "replace@test.com", "Replace Test"));
        Service corte = serviceRepository.saveAndFlush(new Service(user, "Corte", "corte", new BigDecimal("50.00")));
        Service barba = serviceRepository.saveAndFlush(new Service(user, "Barba", "barba", new BigDecimal("30.00")));

        CalendarEvent persisted = new CalendarEvent(
                user,
                "google-event-1",
                "maria - corte",
                "maria - corte",
                Instant.parse("2026-05-08T02:30:00Z"),
                Instant.parse("2026-05-08T03:30:00Z")
        );
        persisted.associateServices(List.of(corte));
        persisted = calendarEventRepository.saveAndFlush(persisted);

        entityManager.clear();

        CalendarEvent managed = loadWithAssociations(user.getId(), "google-event-1");
        managed.associateServices(List.of(barba));

        assertDoesNotThrow(() -> {
            calendarEventServiceLinkRepository.deleteInBulkByCalendarEventIdIn(Set.of(managed.getId()));
            calendarEventServiceLinkRepository.flush();
            calendarEventRepository.saveAll(List.of(managed));
            calendarEventRepository.flush();
        });

        entityManager.clear();

        CalendarEvent reloaded = loadWithAssociations(user.getId(), "google-event-1");
        assertTrue(reloaded.isIdentified());
        assertEquals("Barba", reloaded.getServiceDescriptionSnapshot());
        assertEquals(0, new BigDecimal("30.00").compareTo(reloaded.getServiceValueSnapshot()));
        assertEquals(1, reloaded.getServiceLinks().size());
        assertEquals("Barba", reloaded.getServiceLinks().get(0).getServiceDescriptionSnapshot());
        assertEquals(0, new BigDecimal("30.00").compareTo(reloaded.getServiceLinks().get(0).getServiceValueSnapshot()));
        assertEquals(1, calendarEventServiceLinkRepository.findByCalendarEventId(reloaded.getId()).size());
    }

    @Test
    void shouldClearPersistedServiceLinksThroughCanonicalReplaceFlow() {
        User user = userRepository.saveAndFlush(new User("sub-clear", "clear@test.com", "Clear Test"));
        Service corte = serviceRepository.saveAndFlush(new Service(user, "Corte", "corte", new BigDecimal("50.00")));

        CalendarEvent persisted = new CalendarEvent(
                user,
                "google-event-2",
                "maria - corte",
                "maria - corte",
                Instant.parse("2026-05-08T04:00:00Z"),
                Instant.parse("2026-05-08T05:00:00Z")
        );
        persisted.associateServices(List.of(corte));
        persisted = calendarEventRepository.saveAndFlush(persisted);

        entityManager.clear();

        CalendarEvent managed = loadWithAssociations(user.getId(), "google-event-2");
        managed.clearServiceAssociation();

        assertDoesNotThrow(() -> {
            calendarEventServiceLinkRepository.deleteInBulkByCalendarEventIdIn(Set.of(managed.getId()));
            calendarEventServiceLinkRepository.flush();
            calendarEventRepository.saveAll(List.of(managed));
            calendarEventRepository.flush();
        });

        entityManager.clear();

        CalendarEvent reloaded = loadWithAssociations(user.getId(), "google-event-2");
        assertFalse(reloaded.isIdentified());
        assertEquals(0, reloaded.getServiceLinks().size());
        assertEquals(0, calendarEventServiceLinkRepository.findByCalendarEventId(reloaded.getId()).size());
        assertNull(reloaded.getService());
        assertNull(reloaded.getServiceDescriptionSnapshot());
        assertNull(reloaded.getServiceValueSnapshot());
    }

    private CalendarEvent loadWithAssociations(Long userId, String googleEventId) {
        CalendarEvent event = calendarEventRepository
                .findWithAssociationsByUserIdAndGoogleEventIdIn(userId, List.of(googleEventId))
                .stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected persisted event to be present"));
        assertNotNull(event.getId());
        return event;
    }
}
