package com.api.auth;

import com.api.google.GoogleOAuthClient;
import com.api.google.GoogleOAuthProperties;
import com.api.user.User;
import com.api.user.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthCallbackControllerTest {

    @Mock private GoogleOAuthClient googleOAuthClient;
    @Mock private GoogleIdTokenValidator tokenValidator;
    @Mock private UserRepository userRepository;
    @Mock private OAuthCredentialRepository oauthCredentialRepository;

    private AuthCallbackController controller;

    @BeforeEach
    void setUp() {
        GoogleOAuthProperties properties = new GoogleOAuthProperties(
                "client-id", "client-secret",
                "https://oauth2.googleapis.com/token",
                "https://accounts.google.com/o/oauth2/v2/auth");
        controller = new AuthCallbackController(googleOAuthClient, properties, tokenValidator, userRepository, oauthCredentialRepository);
    }

    @Test
    void shouldRedirectToGoogle() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);

        String html = controller.redirectToGoogle(request);

        assertTrue(html.contains("accounts.google.com"));
        assertTrue(html.contains("client-id"));
        assertTrue(html.contains("calendar.readonly"));
        assertTrue(html.contains("access_type=offline"));
    }

    @Test
    void shouldReturnErrorWhenGoogleReturnsError() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);

        Map<String, Object> result = controller.callback("code", "access_denied", request);

        assertEquals("access_denied", result.get("error"));
    }

    @Test
    void shouldCallbackSuccessfullyWithNewUser() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);

        GoogleTokenResponse tokenResponse = new GoogleTokenResponse();
        tokenResponse.setIdToken("id-token-123");
        tokenResponse.setAccessToken("access-token");
        tokenResponse.setRefreshToken("refresh-token");
        tokenResponse.setExpiresInSeconds(3600L);
        when(googleOAuthClient.exchangeAuthorizationCode(eq("auth-code"), anyString())).thenReturn(tokenResponse);

        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("sub-1");
        payload.setEmail("user@test.com");
        payload.set("name", "User Name");
        when(tokenValidator.validate("id-token-123")).thenReturn(Optional.of(payload));

        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("user@test.com");
        when(user.getName()).thenReturn("User Name");
        when(userRepository.findByGoogleSub("sub-1")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(oauthCredentialRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(oauthCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = controller.callback("auth-code", null, request);

        assertEquals("Login realizado com sucesso!", result.get("message"));
        assertEquals("user@test.com", result.get("email"));
        assertEquals("id-token-123", result.get("idToken"));
    }

    @Test
    void shouldHandleNullNameInCallback() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);

        GoogleTokenResponse tokenResponse = new GoogleTokenResponse();
        tokenResponse.setIdToken("id-token");
        tokenResponse.setAccessToken("access");
        tokenResponse.setRefreshToken("refresh");
        tokenResponse.setExpiresInSeconds(3600L);
        when(googleOAuthClient.exchangeAuthorizationCode(eq("code"), anyString())).thenReturn(tokenResponse);

        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("sub-1");
        payload.setEmail("user@test.com");
        when(tokenValidator.validate("id-token")).thenReturn(Optional.of(payload));

        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getEmail()).thenReturn("user@test.com");
        when(user.getName()).thenReturn("user@test.com");
        when(userRepository.findByGoogleSub("sub-1")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(oauthCredentialRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(oauthCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = controller.callback("code", null, request);

        assertEquals("user@test.com", result.get("name"));
    }

    @Test
    void shouldHandleIOExceptionInCallback() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);

        when(googleOAuthClient.exchangeAuthorizationCode(eq("bad-code"), anyString()))
                .thenThrow(new IOException("network error"));

        Map<String, Object> result = controller.callback("bad-code", null, request);

        assertEquals("OAUTH_EXCHANGE_FAILED", result.get("error"));
    }

    @Test
    void shouldHandleInvalidIdTokenInCallback() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);

        GoogleTokenResponse tokenResponse = new GoogleTokenResponse();
        tokenResponse.setIdToken("bad-id-token");
        tokenResponse.setAccessToken("access");
        tokenResponse.setRefreshToken("refresh");
        tokenResponse.setExpiresInSeconds(3600L);
        when(googleOAuthClient.exchangeAuthorizationCode(eq("code"), anyString())).thenReturn(tokenResponse);
        when(tokenValidator.validate("bad-id-token")).thenReturn(Optional.empty());

        assertThrows(AuthController.InvalidTokenException.class, () ->
                controller.callback("code", null, request));
    }

    @Test
    void shouldOmitPortForStandardPorts() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("example.com");
        request.setServerPort(443);

        String html = controller.redirectToGoogle(request);

        assertTrue(html.contains("redirect_uri=https://example.com/api/auth/callback"));
    }

    @Test
    void shouldUpdateExistingUserInCallback() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("localhost");
        request.setServerPort(8080);

        GoogleTokenResponse tokenResponse = new GoogleTokenResponse();
        tokenResponse.setIdToken("id-token");
        tokenResponse.setAccessToken("access");
        tokenResponse.setRefreshToken("refresh");
        tokenResponse.setExpiresInSeconds(3600L);
        when(googleOAuthClient.exchangeAuthorizationCode(eq("code"), anyString())).thenReturn(tokenResponse);

        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("sub-1");
        payload.setEmail("new@test.com");
        payload.set("name", "New Name");
        when(tokenValidator.validate("id-token")).thenReturn(Optional.of(payload));

        User existing = mock(User.class);
        when(existing.getId()).thenReturn(1L);
        when(existing.getEmail()).thenReturn("new@test.com");
        when(existing.getName()).thenReturn("New Name");
        when(userRepository.findByGoogleSub("sub-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        when(oauthCredentialRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(oauthCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        controller.callback("code", null, request);

        verify(userRepository).save(existing);
    }
}
