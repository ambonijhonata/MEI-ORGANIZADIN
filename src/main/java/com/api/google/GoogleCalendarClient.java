package com.api.google;

import com.api.auth.OAuthCredential;
import com.api.auth.OAuthCredentialRepository;
import com.api.common.IntegrationRevokedException;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GoogleCalendarClient {

    private static final String APP_NAME = "mei-organizadin";
    private static final String NO_CREDS_MSG = "No OAuth credentials found for user";

    private final OAuthCredentialRepository credentialRepo;
    private final GoogleCalendarRequestExecutor requestExecutor;
    private final GoogleOAuthProperties properties;

    public GoogleCalendarClient(
            final GoogleOAuthProperties properties,
            final OAuthCredentialRepository credentialRepo,
            @Value("${google.calendar.sync.max-results:1000}") final int pageSize,
            @Value("${google.calendar.sync.fields:items(id,summary,status,start,end),nextPageToken,nextSyncToken}")
            final String requestFields) {
        this.properties = properties;
        this.credentialRepo = credentialRepo;
        this.requestExecutor = new GoogleCalendarRequestExecutor(pageSize, requestFields);
    }

    public CalendarSyncResult fetchEvents(final Long userId, final String syncTok) throws IOException {
        return fetchEvents(userId, syncTok, null);
    }

    public CalendarSyncResult fetchEvents(
            final Long userId,
            final String syncTok,
            final LocalDate startDate) throws IOException {
        final OAuthCredential credential = loadCredential(userId);
        final Calendar calendarService = buildCalendarService(credential);
        try {
            return requestExecutor.fetch(calendarService, syncTok, startDate);
        } catch (IOException exception) {
            throw GoogleApiErrorMapper.map(exception);
        }
    }

    protected Calendar buildCalendarService(final OAuthCredential credential) {
        @SuppressWarnings("deprecation")
        final GoogleCredential googleCredential = new GoogleCredential.Builder()
                .setTransport(new NetHttpTransport())
                .setJsonFactory(GsonFactory.getDefaultInstance())
                .setClientSecrets(properties.clientId(), properties.clientSecret())
                .build()
                .setAccessToken(credential.getAccessToken())
                .setRefreshToken(credential.getRefreshToken());

        return new Calendar.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                googleCredential
        )
                .setApplicationName(APP_NAME)
                .build();
    }

    private OAuthCredential loadCredential(final Long userId) {
        return credentialRepo.findByUserId(userId)
                .orElseThrow(() -> new IntegrationRevokedException(NO_CREDS_MSG));
    }

    @SuppressWarnings("PMD.LooseCoupling")
    public record CalendarSyncResult(List<Event> events, String nextSyncToken) {
    }

    public static class SyncTokenExpiredException extends IOException {

        private static final long serialVersionUID = 1L;

        public SyncTokenExpiredException(final String message) {
            super(message);
        }

        public SyncTokenExpiredException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    public static class OAuthRevokedException extends IOException {

        private static final long serialVersionUID = 1L;

        public OAuthRevokedException(final String message) {
            super(message);
        }

        public OAuthRevokedException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }

    public static class GoogleApiForbiddenException extends IOException {

        private static final long serialVersionUID = 1L;

        public GoogleApiForbiddenException(final String message) {
            super(message);
        }

        public GoogleApiForbiddenException(final String message, final Throwable cause) {
            super(message, cause);
        }
    }
}
