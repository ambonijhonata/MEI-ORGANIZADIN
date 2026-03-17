package com.api.auth;

import com.api.user.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OAuthCredentialTest {

    @Test
    void shouldCreateWithAllFields() {
        User user = new User("sub", "email@test.com", "Name");
        Instant expiresAt = Instant.now().plusSeconds(3600);
        OAuthCredential cred = new OAuthCredential(user, "access-token", "refresh-token", expiresAt);

        assertEquals(user, cred.getUser());
        assertEquals("access-token", cred.getAccessToken());
        assertEquals("refresh-token", cred.getRefreshToken());
        assertEquals(expiresAt, cred.getExpiresAt());
        assertNull(cred.getId());
    }

    @Test
    void shouldUpdateTokenFields() {
        User user = new User("sub", "email@test.com", "Name");
        OAuthCredential cred = new OAuthCredential(user, "old-access", "old-refresh", Instant.now());

        Instant newExpires = Instant.now().plusSeconds(7200);
        cred.setAccessToken("new-access");
        cred.setRefreshToken("new-refresh");
        cred.setExpiresAt(newExpires);

        assertEquals("new-access", cred.getAccessToken());
        assertEquals("new-refresh", cred.getRefreshToken());
        assertEquals(newExpires, cred.getExpiresAt());
    }
}
