package com.api.google;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RetryableGoogleCalendarClient {

    private static final Logger LOG = LoggerFactory.getLogger(RetryableGoogleCalendarClient.class);
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000;
    private final RetrySleeper retrySleeper;

    public RetryableGoogleCalendarClient() {
        this(Thread::sleep);
    }

    /* default */ RetryableGoogleCalendarClient(final RetrySleeper retrySleeper) { // deterministic tests
        this.retrySleeper = retrySleeper;
    }

    public <T> T executeWithRetry(final IOSupplier<T> action) throws IOException {
        IOException lastFailure = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return action.get();
            } catch (GoogleCalendarClient.OAuthRevokedException
                     | GoogleCalendarClient.SyncTokenExpiredException
                     | GoogleCalendarClient.GoogleApiForbiddenException directFailure) {
                throw directFailure;
            } catch (IOException ioException) {
                lastFailure = ioException;
                if (attempt < MAX_RETRIES) {
                    final long retryDelayMs = calculateRetryDelay(attempt);
                    logRetryFailure(attempt, retryDelayMs);
                    try {
                        retrySleeper.sleep(retryDelayMs);
                    } catch (InterruptedException sleepInterrupted) {
                        Thread.currentThread().interrupt();
                        ioException.addSuppressed(sleepInterrupted);
                        throw ioException;
                    }
                }
            }
        }

        throw lastFailure;
    }

    @FunctionalInterface
    public interface IOSupplier<T> {
        T get() throws IOException;
    }
    private long calculateRetryDelay(final int attempt) {
        return BASE_DELAY_MS << attempt;
    }

    private void logRetryFailure(final int attempt, final long delay) {
        if (LOG.isWarnEnabled()) {
            LOG.warn("google_calendar_retryable_failure attempt={} max_attempts={} retry_delay_ms={}",
                    attempt + 1, MAX_RETRIES + 1, delay);
        }
    }
}

@FunctionalInterface
/* default */ // package-private so tests in this package can inject a controlled sleeper.
interface RetrySleeper {
    void sleep(long delayMs) throws InterruptedException;
}
