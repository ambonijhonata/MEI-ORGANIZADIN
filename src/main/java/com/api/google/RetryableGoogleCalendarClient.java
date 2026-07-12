package com.api.google;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RetryableGoogleCalendarClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryableGoogleCalendarClient.class);
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000;
    private final RetrySleeper retrySleeper;

    public RetryableGoogleCalendarClient() {
        this(Thread::sleep);
    }

    private RetryableGoogleCalendarClient(final RetrySleeper retrySleeper) {
        this.retrySleeper = retrySleeper;
    }

    public <T> T executeWithRetry(final IOSupplier<T> action) throws IOException {
        IOException lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return action.get();
            } catch (GoogleCalendarClient.OAuthRevokedException
                     | GoogleCalendarClient.SyncTokenExpiredException
                     | GoogleCalendarClient.GoogleApiForbiddenException e) {
                throw e;
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    final long delay = BASE_DELAY_MS * (long) Math.pow(2, attempt);
                    logRetryFailure(attempt, delay);
                    try {
                        retrySleeper.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        e.addSuppressed(ie);
                        throw e;
                    }
                }
            }
        }

        throw lastException;
    }

    @FunctionalInterface
    public interface IOSupplier<T> {
        T get() throws IOException;
    }

    @FunctionalInterface
    private interface RetrySleeper {
        void sleep(long delayMs) throws InterruptedException;
    }

    private void logRetryFailure(final int attempt, final long delay) {
        if (LOGGER.isWarnEnabled()) {
            LOGGER.warn("google_calendar_retryable_failure attempt={} max_attempts={} retry_delay_ms={}",
                    attempt + 1, MAX_RETRIES + 1, delay);
        }
    }
}
