package com.api.client;

import com.api.user.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ClientTest {

    private final User user = new User("sub", "email@test.com", "Name");

    @Test
    void shouldCreateClientWithRequiredFields() {
        Client client = new Client(user, "Maria Silva", "maria silva");

        assertEquals("Maria Silva", client.getName());
        assertEquals("maria silva", client.getNormalizedName());
        assertEquals(user, client.getUser());
        assertNull(client.getCpf());
        assertNull(client.getDateOfBirth());
        assertNull(client.getEmail());
        assertNull(client.getPhone());
    }

    @Test
    void shouldSetAllOptionalFields() {
        Client client = new Client(user, "Maria", "maria");

        client.setCpf("12345678901");
        client.setDateOfBirth(LocalDate.of(1990, 5, 15));
        client.setEmail("maria@test.com");
        client.setPhone("11999999999");

        assertEquals("12345678901", client.getCpf());
        assertEquals(LocalDate.of(1990, 5, 15), client.getDateOfBirth());
        assertEquals("maria@test.com", client.getEmail());
        assertEquals("11999999999", client.getPhone());
    }

    @Test
    void shouldUpdateNameAndNormalizedName() {
        Client client = new Client(user, "Old", "old");

        client.setName("New");
        client.setNormalizedName("new");

        assertEquals("New", client.getName());
        assertEquals("new", client.getNormalizedName());
    }

    @Test
    void shouldSetTimestampsOnPrePersist() {
        Client client = new Client(user, "Maria", "maria");

        client.prePersist();

        assertNotNull(client.getCreatedAt());
        assertNotNull(client.getUpdatedAt());
    }

    @Test
    void shouldUpdateTimestampOnPreUpdate() throws InterruptedException {
        Client client = new Client(user, "Maria", "maria");
        client.prePersist();
        var createdAt = client.getCreatedAt();

        Thread.sleep(10);
        client.preUpdate();

        assertEquals(createdAt, client.getCreatedAt());
        assertTrue(client.getUpdatedAt().isAfter(createdAt));
    }
}
