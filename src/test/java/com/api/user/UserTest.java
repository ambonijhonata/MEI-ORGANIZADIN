package com.api.user;

import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void shouldCreateUserWithAllFields() {
        ApplicationUser user = new ApplicationUser("google-sub-123", "user@test.com", "Test User");

        assertEquals("google-sub-123", user.getGoogleSub());
        assertEquals("user@test.com", user.getEmail());
        assertEquals("Test User", user.getName());
        assertNull(user.getId());
    }

    @Test
    void shouldUpdateEmailAndName() {
        ApplicationUser user = new ApplicationUser("sub", "old@test.com", "Old Name");

        user.updateProfile("new@test.com", "New Name");

        assertEquals("new@test.com", user.getEmail());
        assertEquals("New Name", user.getName());
    }

    @Test
    void shouldSetTimestampsOnPrePersist() {
        ApplicationUser user = new ApplicationUser("sub", "email@test.com", "Name");

        user.prePersist();

        assertNotNull(user.getCreatedAt());
        assertNotNull(user.getUpdatedAt());
        assertEquals(user.getCreatedAt(), user.getUpdatedAt());
    }

    @Test
    void shouldUpdateTimestampOnPreUpdate() throws InterruptedException {
        ApplicationUser user = new ApplicationUser("sub", "email@test.com", "Name");
        user.prePersist();
        Instant createdAt = user.getCreatedAt();

        Thread.sleep(10);
        user.preUpdate();

        assertEquals(createdAt, user.getCreatedAt());
        assertTrue(user.getUpdatedAt().isAfter(createdAt));
    }
}
