package com.api.calendar;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ManualAppointmentMigrationIntegrationTest {

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchema() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:manual_appointment_migration_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.execute("DROP TABLE IF EXISTS calendar_events_migration_test");
        jdbcTemplate.execute("""
                CREATE TABLE calendar_events_migration_test (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    google_event_id VARCHAR(1024) NOT NULL,
                    title VARCHAR(1000)
                )
                """);
        jdbcTemplate.execute("""
                ALTER TABLE calendar_events_migration_test
                    ADD CONSTRAINT uk_calendar_events_migration_test UNIQUE (user_id, google_event_id)
                """);
    }

    @Test
    void shouldBackfillSourceAndAllowMultipleManualRowsWithoutGoogleEventId() {
        jdbcTemplate.update("""
                INSERT INTO calendar_events_migration_test (id, user_id, google_event_id, title) VALUES
                (1, 10, 'g-1', 'Google one'),
                (2, 10, 'g-2', 'Google two')
                """);

        jdbcTemplate.execute("""
                ALTER TABLE calendar_events_migration_test
                    ALTER COLUMN google_event_id DROP NOT NULL
                """);
        jdbcTemplate.execute("""
                ALTER TABLE calendar_events_migration_test
                    ADD COLUMN source VARCHAR(20)
                """);
        jdbcTemplate.execute("""
                UPDATE calendar_events_migration_test
                SET source = 'GOOGLE'
                WHERE source IS NULL
                """);
        jdbcTemplate.execute("""
                ALTER TABLE calendar_events_migration_test
                    ALTER COLUMN source SET NOT NULL
                """);

        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM calendar_events_migration_test WHERE source = 'GOOGLE'",
                Integer.class
        ));

        assertDoesNotThrow(() -> jdbcTemplate.update("""
                INSERT INTO calendar_events_migration_test (id, user_id, google_event_id, title, source)
                VALUES (3, 10, NULL, 'Manual one', 'MANUAL')
                """));
        assertDoesNotThrow(() -> jdbcTemplate.update("""
                INSERT INTO calendar_events_migration_test (id, user_id, google_event_id, title, source)
                VALUES (4, 10, NULL, 'Manual two', 'MANUAL')
                """));

        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM calendar_events_migration_test WHERE source = 'MANUAL' AND google_event_id IS NULL",
                Integer.class
        ));
    }
}
