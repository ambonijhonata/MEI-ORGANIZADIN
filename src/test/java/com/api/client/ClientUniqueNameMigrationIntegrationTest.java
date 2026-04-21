package com.api.client;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientUniqueNameMigrationIntegrationTest {

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpSchema() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:client_migration_test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        jdbcTemplate = new JdbcTemplate(dataSource);

        jdbcTemplate.execute("DROP TABLE IF EXISTS clients_migration_test");
        jdbcTemplate.execute("""
                CREATE TABLE clients_migration_test (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    normalized_name VARCHAR(500) NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
    }

    @Test
    void shouldSanitizeDuplicatesBeforeApplyingUniqueConstraint() {
        jdbcTemplate.update("""
                INSERT INTO clients_migration_test (id, user_id, normalized_name, created_at) VALUES
                (1, 1, 'maria silva', TIMESTAMP '2026-04-20 10:00:00'),
                (2, 1, 'maria silva', TIMESTAMP '2026-04-20 10:00:01'),
                (3, 1, 'ana', TIMESTAMP '2026-04-20 10:00:02'),
                (4, 2, 'maria silva', TIMESTAMP '2026-04-20 10:00:03')
                """);

        List<Long> duplicateIds = jdbcTemplate.queryForList("""
                SELECT id FROM (
                    SELECT
                        id,
                        ROW_NUMBER() OVER (
                            PARTITION BY user_id, normalized_name
                            ORDER BY created_at, id
                        ) AS rn
                    FROM clients_migration_test
                ) ranked_clients
                WHERE rn > 1
                ORDER BY id
                """, Long.class);

        for (Long id : duplicateIds) {
            jdbcTemplate.update("""
                    UPDATE clients_migration_test
                    SET normalized_name = LEFT(normalized_name, 450) || '__legacy__' || id
                    WHERE id = ?
                    """, id);
        }

        List<String> normalizedNames = jdbcTemplate.queryForList(
                "SELECT normalized_name FROM clients_migration_test ORDER BY id",
                String.class
        );
        assertEquals(List.of("maria silva", "maria silva__legacy__2", "ana", "maria silva"), normalizedNames);

        jdbcTemplate.execute("""
                ALTER TABLE clients_migration_test
                    ADD CONSTRAINT uk_clients_migration_test UNIQUE (user_id, normalized_name)
                """);

        assertThrows(DataAccessException.class, () ->
                jdbcTemplate.update("""
                        INSERT INTO clients_migration_test (id, user_id, normalized_name, created_at)
                        VALUES (5, 1, 'maria silva', TIMESTAMP '2026-04-20 10:00:04')
                        """));
    }
}
