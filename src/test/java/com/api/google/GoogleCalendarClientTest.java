package com.api.google;

import com.api.auth.OAuthCredential;
import com.api.auth.OAuthCredentialRepository;
import com.api.user.User;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.calendar.Calendar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleCalendarClientTest {

    @Mock private GoogleOAuthProperties properties;
    @Mock private OAuthCredentialRepository oauthCredentialRepository;
    @Mock private Calendar calendarService;
    @Mock private Calendar.Events calendarEvents;
    @Mock private Calendar.Events.List listRequest;

    private GoogleCalendarClient client;

    @BeforeEach
    void setUp() throws IOException {
        client = new TestableGoogleCalendarClient(properties, oauthCredentialRepository, calendarService);

        lenient().when(calendarService.events()).thenReturn(calendarEvents);
        lenient().when(calendarEvents.list(anyString())).thenReturn(listRequest);
        lenient().when(listRequest.setSingleEvents(anyBoolean())).thenReturn(listRequest);
        lenient().when(listRequest.setOrderBy(anyString())).thenReturn(listRequest);
        lenient().when(listRequest.setMaxResults(anyInt())).thenReturn(listRequest);
        lenient().when(listRequest.setFields(anyString())).thenReturn(listRequest);
        lenient().when(listRequest.setPageToken(nullable(String.class))).thenReturn(listRequest);
        lenient().when(listRequest.setSyncToken(nullable(String.class))).thenReturn(listRequest);

        User user = new User("sub", "user@test.com", "User");
        OAuthCredential credential = new OAuthCredential(user, "access", "refresh", Instant.now().plusSeconds(3600));
        when(oauthCredentialRepository.findByUserId(1L)).thenReturn(Optional.of(credential));
    }

    @Test
    void shouldThrowOAuthRevokedExceptionFor401WithReason() throws IOException {
        when(listRequest.execute()).thenThrow(googleError(401, "Unauthorized",
                "Request had invalid authentication credentials.", "authError"));

        GoogleCalendarClient.OAuthRevokedException ex = assertThrows(
                GoogleCalendarClient.OAuthRevokedException.class,
                () -> client.fetchEvents(1L, null)
        );

        assertEquals("Request had invalid authentication credentials. (reason: authError)", ex.getMessage());
    }

    @Test
    void shouldThrowGoogleApiForbiddenExceptionFor403WithReason() throws IOException {
        when(listRequest.execute()).thenThrow(googleError(403, "Forbidden",
                "Google Calendar API has not been used in project.", "accessNotConfigured"));

        GoogleCalendarClient.GoogleApiForbiddenException ex = assertThrows(
                GoogleCalendarClient.GoogleApiForbiddenException.class,
                () -> client.fetchEvents(1L, null)
        );

        assertEquals("Google Calendar API has not been used in project. (reason: accessNotConfigured)", ex.getMessage());
    }

    @Test
    void shouldFallbackToStatusMessageWhenGoogleDetailsAreMissing() throws IOException {
        when(listRequest.execute()).thenThrow(googleErrorWithoutDetails(403, "Forbidden"));

        GoogleCalendarClient.GoogleApiForbiddenException ex = assertThrows(
                GoogleCalendarClient.GoogleApiForbiddenException.class,
                () -> client.fetchEvents(1L, null)
        );

        assertEquals("Forbidden", ex.getMessage());
    }

    @Test
    void shouldThrowSyncTokenExpiredExceptionFor410() throws IOException {
        when(listRequest.execute()).thenThrow(googleError(410, "Gone", "Sync token is no longer valid.", "fullSyncRequired"));

        GoogleCalendarClient.SyncTokenExpiredException ex = assertThrows(
                GoogleCalendarClient.SyncTokenExpiredException.class,
                () -> client.fetchEvents(1L, "expired-token")
        );

        assertEquals("Sync token expired, full resync required", ex.getMessage());
    }

    private static GoogleJsonResponseException googleError(
            int statusCode,
            String statusMessage,
            String detailMessage,
            String reason
    ) {
        GoogleJsonError details = new GoogleJsonError();
        details.setCode(statusCode);
        details.setMessage(detailMessage);

        GoogleJsonError.ErrorInfo errorInfo = new GoogleJsonError.ErrorInfo();
        errorInfo.setReason(reason);
        details.setErrors(List.of(errorInfo));

        HttpResponseException.Builder builder =
                new HttpResponseException.Builder(statusCode, statusMessage, new HttpHeaders());
        return new GoogleJsonResponseException(builder, details);
    }

    private static GoogleJsonResponseException googleErrorWithoutDetails(int statusCode, String statusMessage) {
        HttpResponseException.Builder builder =
                new HttpResponseException.Builder(statusCode, statusMessage, new HttpHeaders());
        return new GoogleJsonResponseException(builder, null);
    }

    private static final class TestableGoogleCalendarClient extends GoogleCalendarClient {
        private final Calendar calendarService;

        private TestableGoogleCalendarClient(
                GoogleOAuthProperties properties,
                OAuthCredentialRepository oauthCredentialRepository,
                Calendar calendarService
        ) {
            super(
                    properties,
                    oauthCredentialRepository,
                    1000,
                    "items(id,summary,status,start,end),nextPageToken,nextSyncToken"
            );
            this.calendarService = calendarService;
        }

        @Override
        Calendar buildCalendarService(OAuthCredential credential) {
            return calendarService;
        }
    }
}
