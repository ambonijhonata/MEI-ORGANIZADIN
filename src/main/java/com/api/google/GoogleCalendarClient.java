package com.api.google;

import com.api.auth.OAuthCredential;
import com.api.auth.OAuthCredentialRepository;
import com.api.common.IntegrationRevokedException;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class GoogleCalendarClient {

    private static final int DEFAULT_MAX_RESULTS = 1000;
    private static final String DEFAULT_FIELDS = "items(id,summary,status,start,end),nextPageToken,nextSyncToken";

    private final GoogleOAuthProperties properties;
    private final OAuthCredentialRepository oauthCredentialRepository;
    private final int maxResults;
    private final String requestFields;

    public GoogleCalendarClient(GoogleOAuthProperties properties,
                                OAuthCredentialRepository oauthCredentialRepository,
                                @Value("${google.calendar.sync.max-results:" + DEFAULT_MAX_RESULTS + "}") int maxResults,
                                @Value("${google.calendar.sync.fields:" + DEFAULT_FIELDS + "}") String requestFields) {
        this.properties = properties;
        this.oauthCredentialRepository = oauthCredentialRepository;
        this.maxResults = maxResults <= 0 ? DEFAULT_MAX_RESULTS : maxResults;
        this.requestFields = (requestFields == null || requestFields.isBlank()) ? DEFAULT_FIELDS : requestFields;
    }

    public CalendarSyncResult fetchEvents(Long userId, String syncToken) throws IOException {
        OAuthCredential credential = oauthCredentialRepository.findByUserId(userId)
                .orElseThrow(() -> new IntegrationRevokedException("No OAuth credentials found for user"));

        Calendar calendarService = buildCalendarService(credential);

        List<Event> allEvents = new ArrayList<>();
        String pageToken = null;
        String nextSyncToken = null;

        try {
            do {
                Calendar.Events.List request = calendarService.events().list("primary")
                        .setSingleEvents(true)
                        .setOrderBy("startTime")
                        .setMaxResults(maxResults)
                        .setFields(requestFields);

                if (syncToken != null) {
                    request.setSyncToken(syncToken);
                } else {
                    request.setPageToken(pageToken);
                }

                if (pageToken != null) {
                    request.setPageToken(pageToken);
                }

                Events events = request.execute();
                if (events.getItems() != null) {
                    allEvents.addAll(events.getItems());
                }
                pageToken = events.getNextPageToken();
                nextSyncToken = events.getNextSyncToken();

            } while (pageToken != null);

        } catch (GoogleJsonResponseException e) {
            int statusCode = e.getStatusCode();
            if (statusCode == 410) {
                throw new SyncTokenExpiredException("Sync token expired, full resync required");
            }
            String googleMessage = extractGoogleErrorMessage(e);
            if (statusCode == 401) {
                throw new OAuthRevokedException(googleMessage);
            }
            if (statusCode == 403) {
                throw new GoogleApiForbiddenException(googleMessage);
            }
            throw e;
        }

        return new CalendarSyncResult(allEvents, nextSyncToken);
    }

    Calendar buildCalendarService(OAuthCredential credential) {
        @SuppressWarnings("deprecation")
        GoogleCredential googleCredential = new GoogleCredential.Builder()
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
                .setApplicationName("mei-organizadin")
                .build();
    }

    private String extractGoogleErrorMessage(GoogleJsonResponseException e) {
        GoogleJsonError details = e.getDetails();
        if (details != null) {
            String detailMessage = details.getMessage();
            if (details.getErrors() != null && !details.getErrors().isEmpty()) {
                GoogleJsonError.ErrorInfo firstError = details.getErrors().get(0);
                String reason = firstError.getReason();
                if (detailMessage != null && reason != null && !reason.isBlank()) {
                    return detailMessage + " (reason: " + reason + ")";
                }
            }
            if (detailMessage != null && !detailMessage.isBlank()) {
                return detailMessage;
            }
        }

        String content = e.getContent();
        if (content != null && !content.isBlank()) {
            return content;
        }

        String statusMessage = e.getStatusMessage();
        if (statusMessage != null && !statusMessage.isBlank()) {
            return statusMessage;
        }

        return "Unknown Google API error";
    }

    public record CalendarSyncResult(List<Event> events, String nextSyncToken) {}

    public static class SyncTokenExpiredException extends IOException {
        public SyncTokenExpiredException(String message) { super(message); }
    }

    public static class OAuthRevokedException extends IOException {
        public OAuthRevokedException(String message) { super(message); }
    }

    public static class GoogleApiForbiddenException extends IOException {
        public GoogleApiForbiddenException(String message) { super(message); }
    }
}
