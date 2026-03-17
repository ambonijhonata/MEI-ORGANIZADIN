package com.api.google;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class RetryableGoogleCalendarClientTest {

    private RetryableGoogleCalendarClient retryClient;

    @BeforeEach
    void setUp() {
        retryClient = new RetryableGoogleCalendarClient();
    }

    @Test
    void shouldReturnResultOnFirstAttempt() throws IOException {
        String result = retryClient.executeWithRetry(() -> "success");
        assertEquals("success", result);
    }

    @Test
    void shouldRetryAndSucceedOnSecondAttempt() throws IOException {
        var counter = new int[]{0};
        String result = retryClient.executeWithRetry(() -> {
            counter[0]++;
            if (counter[0] == 1) throw new IOException("transient error");
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(2, counter[0]);
    }

    @Test
    void shouldThrowAfterMaxRetries() {
        assertThrows(IOException.class, () ->
                retryClient.executeWithRetry(() -> {
                    throw new IOException("persistent error");
                })
        );
    }

    @Test
    void shouldNotRetryOAuthRevokedException() {
        var counter = new int[]{0};
        assertThrows(GoogleCalendarClient.OAuthRevokedException.class, () ->
                retryClient.executeWithRetry(() -> {
                    counter[0]++;
                    throw new GoogleCalendarClient.OAuthRevokedException("revoked");
                })
        );
        assertEquals(1, counter[0]);
    }

    @Test
    void shouldNotRetrySyncTokenExpiredException() {
        var counter = new int[]{0};
        assertThrows(GoogleCalendarClient.SyncTokenExpiredException.class, () ->
                retryClient.executeWithRetry(() -> {
                    counter[0]++;
                    throw new GoogleCalendarClient.SyncTokenExpiredException("expired");
                })
        );
        assertEquals(1, counter[0]);
    }
}
