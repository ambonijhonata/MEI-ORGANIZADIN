package com.api.auth;

import com.api.calendar.SyncState;
import com.api.calendar.SyncStateRepository;
import com.api.calendar.SyncStatus;
import com.api.google.GoogleOAuthClient;
import com.api.user.User;
import com.api.user.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private GoogleIdTokenValidator tokenValidator;
    @Mock private UserRepository userRepository;
    @Mock private GoogleOAuthClient googleOAuthClient;
    @Mock private OAuthCredentialRepository oauthCredentialRepository;
    @Mock private SyncStateRepository syncStateRepository;
    @Mock private AccessTokenService accessTokenService;
    @Mock private RefreshTokenService refreshTokenService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(tokenValidator, userRepository, googleOAuthClient,
                oauthCredentialRepository, syncStateRepository, accessTokenService, refreshTokenService);
    }

    @Test
    void shouldLoginSuccessfullyWithNewUser() throws IOException {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-1");
        payload.setEmail("user@test.com");
        payload.set("name", "Test User");

        when(tokenValidator.validate("valid-token")).thenReturn(Optional.of(payload));

        User user = new User("google-sub-1", "user@test.com", "Test User");
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(user);

        GoogleTokenResponse tokenResponse = new GoogleTokenResponse();
        tokenResponse.setAccessToken("access-token");
        tokenResponse.setRefreshToken("refresh-token");
        tokenResponse.setExpiresInSeconds(3600L);
        when(googleOAuthClient.exchangeAuthorizationCode("auth-code")).thenReturn(tokenResponse);
        when(oauthCredentialRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(oauthCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(syncStateRepository.findByUserId(any())).thenReturn(Optional.empty());

        var request = new AuthController.LoginRequest("valid-token", "auth-code", null, null);
        stubSessionIssuance(user);
        var response = authController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("user@test.com", response.getBody().email());
        assertEquals("Test User", response.getBody().name());
        assertEquals("access-session-token", response.getBody().accessToken());
        assertEquals("refresh-session-token", response.getBody().refreshToken());
    }

    @Test
    void shouldLoginSuccessfullyWithExistingUser() throws IOException {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-1");
        payload.setEmail("new@test.com");
        payload.set("name", "Updated");

        when(tokenValidator.validate("valid-token")).thenReturn(Optional.of(payload));

        User existing = new User("google-sub-1", "old@test.com", "Old");
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        GoogleTokenResponse tokenResponse = new GoogleTokenResponse();
        tokenResponse.setAccessToken("access");
        tokenResponse.setRefreshToken("refresh");
        tokenResponse.setExpiresInSeconds(3600L);
        when(googleOAuthClient.exchangeAuthorizationCode("auth-code")).thenReturn(tokenResponse);
        when(oauthCredentialRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(oauthCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(syncStateRepository.findByUserId(any())).thenReturn(Optional.empty());

        var request = new AuthController.LoginRequest("valid-token", "auth-code", null, null);
        stubSessionIssuance(existing);
        var response = authController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository).save(existing);
    }

    @Test
    void shouldUseEmailWhenNameIsNull() throws IOException {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-1");
        payload.setEmail("user@test.com");

        when(tokenValidator.validate("valid-token")).thenReturn(Optional.of(payload));

        User user = new User("google-sub-1", "user@test.com", "user@test.com");
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(user);

        GoogleTokenResponse tokenResponse = new GoogleTokenResponse();
        tokenResponse.setAccessToken("access");
        tokenResponse.setRefreshToken("refresh");
        tokenResponse.setExpiresInSeconds(3600L);
        when(googleOAuthClient.exchangeAuthorizationCode("auth-code")).thenReturn(tokenResponse);
        when(oauthCredentialRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(oauthCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(syncStateRepository.findByUserId(any())).thenReturn(Optional.empty());

        var request = new AuthController.LoginRequest("valid-token", "auth-code", null, null);
        stubSessionIssuance(user);
        var response = authController.login(request);

        assertEquals("user@test.com", response.getBody().name());
    }

    @Test
    void shouldThrowInvalidTokenOnBadToken() {
        when(tokenValidator.validate("bad-token")).thenReturn(Optional.empty());

        var request = new AuthController.LoginRequest("bad-token", "auth-code", null, null);
        assertThrows(AuthController.InvalidTokenException.class, () -> authController.login(request));
    }

    @Test
    void shouldThrowOAuthExchangeOnIOException() throws IOException {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-1");
        payload.setEmail("user@test.com");
        payload.set("name", "User");

        when(tokenValidator.validate("valid-token")).thenReturn(Optional.of(payload));

        User user = new User("google-sub-1", "user@test.com", "User");
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(googleOAuthClient.exchangeAuthorizationCode("bad-code")).thenThrow(new IOException("exchange failed"));

        var request = new AuthController.LoginRequest("valid-token", "bad-code", null, null);
        assertThrows(AuthController.OAuthExchangeException.class, () -> authController.login(request));
    }

    @Test
    void shouldClearReauthRequiredStatusOnLogin() throws IOException {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-1");
        payload.setEmail("user@test.com");
        payload.set("name", "User");

        when(tokenValidator.validate("valid-token")).thenReturn(Optional.of(payload));

        User user = new User("google-sub-1", "user@test.com", "User");
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        GoogleTokenResponse tokenResponse = new GoogleTokenResponse();
        tokenResponse.setAccessToken("access");
        tokenResponse.setRefreshToken("refresh");
        tokenResponse.setExpiresInSeconds(3600L);
        when(googleOAuthClient.exchangeAuthorizationCode("auth-code")).thenReturn(tokenResponse);
        when(oauthCredentialRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(oauthCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncState syncState = new SyncState(user);
        syncState.markReauthRequired("revoked");
        when(syncStateRepository.findByUserId(any())).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any())).thenReturn(syncState);

        var request = new AuthController.LoginRequest("valid-token", "auth-code", null, null);
        stubSessionIssuance(user);
        authController.login(request);

        assertEquals(SyncStatus.SYNCED, syncState.getStatus());
        assertNull(syncState.getErrorCategory());
        assertNull(syncState.getErrorMessage());
    }

    @Test
    void shouldUpdateExistingOAuthCredentialOnLogin() throws IOException {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setSubject("google-sub-1");
        payload.setEmail("user@test.com");
        payload.set("name", "User");

        when(tokenValidator.validate("valid-token")).thenReturn(Optional.of(payload));

        User user = new User("google-sub-1", "user@test.com", "User");
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        GoogleTokenResponse tokenResponse = new GoogleTokenResponse();
        tokenResponse.setAccessToken("new-access");
        tokenResponse.setRefreshToken("new-refresh");
        tokenResponse.setExpiresInSeconds(3600L);
        when(googleOAuthClient.exchangeAuthorizationCode("auth-code")).thenReturn(tokenResponse);

        OAuthCredential existingCred = new OAuthCredential(user, "old-access", "old-refresh", java.time.Instant.now());
        when(oauthCredentialRepository.findByUserId(any())).thenReturn(Optional.of(existingCred));
        when(oauthCredentialRepository.save(any())).thenReturn(existingCred);
        when(syncStateRepository.findByUserId(any())).thenReturn(Optional.empty());

        var request = new AuthController.LoginRequest("valid-token", "auth-code", null, null);
        stubSessionIssuance(user);
        authController.login(request);

        assertEquals("new-access", existingCred.getAccessToken());
        assertEquals("new-refresh", existingCred.getRefreshToken());
    }

    @Test
    void logoutShouldRevokeRefreshToken() {
        var request = new AuthController.LogoutRequest("refresh-session-token");

        var response = authController.logout(request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(refreshTokenService).revoke("refresh-session-token", "LOGOUT");
    }

    @Test
    void refreshShouldIssueAccessTokenFromDetachedPrincipal() {
        var request = new AuthController.RefreshRequest("refresh-token", null, null);
        var principal = new AuthenticatedUser(1L, "g-sub", "user@test.com", "User");
        var issuedRefresh = new RefreshTokenService.IssuedRefreshToken(
                "new-refresh",
                Instant.now().plusSeconds(3600),
                UUID.randomUUID(),
                principal
        );
        when(refreshTokenService.rotate(eq("refresh-token"), any()))
                .thenReturn(RefreshTokenService.RotationResult.success(issuedRefresh));
        when(accessTokenService.issue(principal))
                .thenReturn(new AccessTokenService.IssuedAccessToken("new-access", Instant.now().plusSeconds(900)));

        var response = authController.refresh(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("new-access", response.getBody().accessToken());
        assertEquals("new-refresh", response.getBody().refreshToken());
    }

    @Test
    void refreshShouldTranslateRuntimeFailuresToRetryableException() {
        var request = new AuthController.RefreshRequest("refresh-token", null, null);
        when(refreshTokenService.rotate(eq("refresh-token"), any()))
                .thenThrow(new RuntimeException("database unavailable"));

        var ex = assertThrows(AuthController.RefreshRetryableException.class, () -> authController.refresh(request));
        assertEquals("Refresh temporarily unavailable", ex.getMessage());
    }

    @Test
    void refreshShouldKeepTerminalStatusesAsRefreshTokenException() {
        var request = new AuthController.RefreshRequest("refresh-token", null, null);
        when(refreshTokenService.rotate(eq("refresh-token"), any()))
                .thenReturn(RefreshTokenService.RotationResult.expired());

        var ex = assertThrows(AuthController.RefreshTokenException.class, () -> authController.refresh(request));
        assertEquals("REFRESH_TOKEN_EXPIRED", ex.getCode());
    }

    private void stubSessionIssuance(User user) {
        when(accessTokenService.issue(any())).thenReturn(
                new AccessTokenService.IssuedAccessToken("access-session-token", java.time.Instant.now().plusSeconds(900))
        );
        when(refreshTokenService.issueForUser(eq(user), any())).thenReturn(
                new RefreshTokenService.IssuedRefreshToken(
                        "refresh-session-token",
                        java.time.Instant.now().plusSeconds(2592000),
                        java.util.UUID.randomUUID(),
                        new AuthenticatedUser(user.getId(), user.getGoogleSub(), user.getEmail(), user.getName())
                )
        );
    }
}
