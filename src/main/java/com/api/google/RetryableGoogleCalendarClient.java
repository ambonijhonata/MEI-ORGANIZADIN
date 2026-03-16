package com.api.google;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.function.Supplier;

@Component
public class RetryableGoogleCalendarClient {

    private static final Logger log = LoggerFactory.getLogger(RetryableGoogleCalendarClient.class);
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 1000;

    public <T> T executeWithRetry(IOSupplier<T> action) throws IOException {
        IOException lastException = null;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return action.get();
            } catch (GoogleCalendarClient.OAuthRevokedException | GoogleCalendarClient.SyncTokenExpiredException e) {
                throw e;
            } catch (IOException e) {
                lastException = e;
                if (attempt < MAX_RETRIES) {
                    long delay = BASE_DELAY_MS * (long) Math.pow(2, attempt);
                    log.warn("Google API call failed (attempt {}/{}), retrying in {}ms: {}",
                            attempt + 1, MAX_RETRIES + 1, delay, e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Retry interrupted", ie);
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
}
