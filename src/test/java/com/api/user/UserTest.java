package com.api.user;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void shouldCreateUserWithAllFields() {
        User user = new User("google-sub-123", "user@test.com", "Test User");

        assertEquals("google-sub-123", user.getGoogleSub());
        assertEquals("user@test.com", user.getEmail());
        assertEquals("Test User", user.getName());
        assertNull(user.getId());
    }

    @Test
    void shouldUpdateEmailAndName() {
        User user = new User("sub", "old@test.com", "Old Name");

        user.setEmail("new@test.com");
        user.setName("New Name");

        assertEquals("new@test.com", user.getEmail());
        assertEquals("New Name", user.getName());
    }

    @Test
    void shouldSetTimestampsOnPrePersist() {
        User user = new User("sub", "email@test.com", "Name");

        user.prePersist();

        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
        assertEquals(user.getCreatedAt(), user.getUpdatedAt());
    }

    @Test
    void shouldUpdateTimestampOnPreUpdate() throws InterruptedException {
        User user = new User("sub", "email@test.com", "Name");
        user.prePersist();
        var createdAt = user.getCreatedAt();

        Thread.sleep(10);
        user.preUpdate();

        assertEquals(createdAt, user.getCreatedAt());
        assertTrue(user.getUpdatedAt().isAfter(createdAt));
    }
}
