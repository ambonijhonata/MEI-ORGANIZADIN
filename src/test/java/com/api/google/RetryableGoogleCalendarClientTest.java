package com.api.google;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

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
        final AtomicInteger attempts = new AtomicInteger();
        String result = retryClient.executeWithRetry(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IOException("transient error");
            }
            return "recovered";
        });

        assertEquals("recovered", result);
        assertEquals(2, attempts.get());
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
        final AtomicInteger attempts = new AtomicInteger();
        assertThrows(GoogleCalendarClient.OAuthRevokedException.class, () ->
                retryClient.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    throw new GoogleCalendarClient.OAuthRevokedException("revoked");
                })
        );
        assertEquals(1, attempts.get());
    }

    @Test
    void shouldNotRetrySyncTokenExpiredException() {
        final AtomicInteger attempts = new AtomicInteger();
        assertThrows(GoogleCalendarClient.SyncTokenExpiredException.class, () ->
                retryClient.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    throw new GoogleCalendarClient.SyncTokenExpiredException("expired");
                })
        );
        assertEquals(1, attempts.get());
    }

    @Test
    void shouldNotRetryGoogleApiForbiddenException() {
        final AtomicInteger attempts = new AtomicInteger();
        assertThrows(GoogleCalendarClient.GoogleApiForbiddenException.class, () ->
                retryClient.executeWithRetry(() -> {
                    attempts.incrementAndGet();
                    throw new GoogleCalendarClient.GoogleApiForbiddenException("access denied");
                })
        );
        assertEquals(1, attempts.get());
    }

    @Test
    void shouldPreserveIOExceptionAndInterruptThreadWhenSleepIsInterrupted() {
        retryClient = new RetryableGoogleCalendarClient(delayMs -> {
            throw new InterruptedException("sleep interrupted");
        });

        IOException exception = assertThrows(IOException.class, () ->
                retryClient.executeWithRetry(() -> {
                    throw new IOException("transient error");
                })
        );

        assertEquals("transient error", exception.getMessage());
        assertEquals(1, exception.getSuppressed().length);
        assertInstanceOf(InterruptedException.class, exception.getSuppressed()[0]);
        assertTrue(Thread.currentThread().isInterrupted());
        Thread.interrupted();
    }
}
