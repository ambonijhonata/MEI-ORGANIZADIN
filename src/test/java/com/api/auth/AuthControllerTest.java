package com.api.auth;

import com.api.calendar.CalendarIntegrationStatusMapper;
import com.api.calendar.SyncState;
import com.api.calendar.SyncStateRepository;
import com.api.calendar.SyncStatus;
import com.api.google.GoogleOAuthClient;
import com.api.user.ApplicationUser;
import com.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.RecoverableDataAccessException;
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
        final AuthAccountService accountSvc = new AuthAccountService(
                userRepository,
                googleOAuthClient,
                oauthCredentialRepository,
                syncStateRepository
        );
        authController = new AuthController(
                tokenValidator,
                accountSvc,
                accessTokenService,
                refreshTokenService
        );
    }

    @Test
    void shouldLoginSuccessfullyWithNewUser() throws IOException {
        when(tokenValidator.validateProfile("valid-token"))
                .thenReturn(Optional.of(new GoogleUserProfile("google-sub-1", "user@test.com", "Test ApplicationUser")));

        ApplicationUser user = new ApplicationUser("google-sub-1", "user@test.com", "Test ApplicationUser");
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.save(any(ApplicationUser.class))).thenReturn(user);

        when(googleOAuthClient.exchangeAuthorizationCodeResult("auth-code", ""))
                .thenReturn(exchangeResult("access-token", "refresh-token", 3600L));
        when(oauthCredentialRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(oauthCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(syncStateRepository.findByUserId(any())).thenReturn(Optional.empty());

        var request = new AuthLoginRequest("valid-token", "auth-code", null, null);
        stubSessionIssuance(user);
        var response = authController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("user@test.com", response.getBody().email());
        assertEquals("Test ApplicationUser", response.getBody().name());
        assertEquals("access-session-token", response.getBody().accessToken());
        assertEquals("refresh-session-token", response.getBody().refreshToken());
    }

    @Test
    void shouldLoginSuccessfullyWithExistingUser() throws IOException {
        when(tokenValidator.validateProfile("valid-token"))
                .thenReturn(Optional.of(new GoogleUserProfile("google-sub-1", "new@test.com", "Updated")));

        ApplicationUser existing = new ApplicationUser("google-sub-1", "old@test.com", "Old");
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);

        when(googleOAuthClient.exchangeAuthorizationCodeResult("auth-code", ""))
                .thenReturn(exchangeResult("access", "refresh", 3600L));
        when(oauthCredentialRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(oauthCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(syncStateRepository.findByUserId(any())).thenReturn(Optional.empty());

        var request = new AuthLoginRequest("valid-token", "auth-code", null, null);
        stubSessionIssuance(existing);
        var response = authController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(userRepository).save(existing);
    }

    @Test
    void shouldUseEmailWhenNameIsNull() throws IOException {
        when(tokenValidator.validateProfile("valid-token"))
                .thenReturn(Optional.of(new GoogleUserProfile("google-sub-1", "user@test.com", "user@test.com")));

        ApplicationUser user = new ApplicationUser("google-sub-1", "user@test.com", "user@test.com");
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.save(any(ApplicationUser.class))).thenReturn(user);

        when(googleOAuthClient.exchangeAuthorizationCodeResult("auth-code", ""))
                .thenReturn(exchangeResult("access", "refresh", 3600L));
        when(oauthCredentialRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(oauthCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(syncStateRepository.findByUserId(any())).thenReturn(Optional.empty());

        var request = new AuthLoginRequest("valid-token", "auth-code", null, null);
        stubSessionIssuance(user);
        var response = authController.login(request);

        assertEquals("user@test.com", response.getBody().name());
    }

    @Test
    void shouldThrowInvalidTokenOnBadToken() {
        when(tokenValidator.validateProfile("bad-token")).thenReturn(Optional.empty());

        var request = new AuthLoginRequest("bad-token", "auth-code", null, null);
        assertThrows(InvalidTokenException.class, () -> authController.login(request));
    }

    @Test
    void shouldThrowOAuthExchangeOnIOException() throws IOException {
        when(tokenValidator.validateProfile("valid-token"))
                .thenReturn(Optional.of(new GoogleUserProfile("google-sub-1", "user@test.com", "ApplicationUser")));

        ApplicationUser user = new ApplicationUser("google-sub-1", "user@test.com", "ApplicationUser");
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.save(any(ApplicationUser.class))).thenReturn(user);
        when(googleOAuthClient.exchangeAuthorizationCodeResult("bad-code", ""))
                .thenThrow(new IOException("exchange failed"));

        var request = new AuthLoginRequest("valid-token", "bad-code", null, null);
        assertThrows(OAuthExchangeException.class, () -> authController.login(request));
    }

    @Test
    void shouldClearReauthRequiredStatusOnLogin() throws IOException {
        when(tokenValidator.validateProfile("valid-token"))
                .thenReturn(Optional.of(new GoogleUserProfile("google-sub-1", "user@test.com", "ApplicationUser")));

        ApplicationUser user = new ApplicationUser("google-sub-1", "user@test.com", "ApplicationUser");
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        when(googleOAuthClient.exchangeAuthorizationCodeResult("auth-code", ""))
                .thenReturn(exchangeResult("access", "refresh", 3600L));
        when(oauthCredentialRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(oauthCredentialRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncState syncState = new SyncState(user);
        syncState.operationalState().markReauthRequired("revoked");
        when(syncStateRepository.findByUserId(any())).thenReturn(Optional.of(syncState));
        when(syncStateRepository.save(any())).thenReturn(syncState);

        var request = new AuthLoginRequest("valid-token", "auth-code", null, null);
        stubSessionIssuance(user);
        authController.login(request);

        assertFalse(syncState.operationalState().isReauthRequired());
        assertEquals(SyncStatus.SYNCED.name(), CalendarIntegrationStatusMapper.toReadModel(syncState).status());
        assertNull(CalendarIntegrationStatusMapper.toReadModel(syncState).errorCategory());
        assertNull(CalendarIntegrationStatusMapper.toReadModel(syncState).errorMessage());
    }

    @Test
    void shouldUpdateExistingOAuthCredentialOnLogin() throws IOException {
        when(tokenValidator.validateProfile("valid-token"))
                .thenReturn(Optional.of(new GoogleUserProfile("google-sub-1", "user@test.com", "ApplicationUser")));

        ApplicationUser user = new ApplicationUser("google-sub-1", "user@test.com", "ApplicationUser");
        when(userRepository.findByGoogleSub("google-sub-1")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        when(googleOAuthClient.exchangeAuthorizationCodeResult("auth-code", ""))
                .thenReturn(exchangeResult("new-access", "new-refresh", 3600L));

        OAuthCredential existingCred = new OAuthCredential(user, "old-access", "old-refresh", java.time.Instant.now());
        when(oauthCredentialRepository.findByUserId(any())).thenReturn(Optional.of(existingCred));
        when(oauthCredentialRepository.save(any())).thenReturn(existingCred);
        when(syncStateRepository.findByUserId(any())).thenReturn(Optional.empty());

        var request = new AuthLoginRequest("valid-token", "auth-code", null, null);
        stubSessionIssuance(user);
        authController.login(request);

        assertEquals("new-access", existingCred.getAccessToken());
        assertEquals("new-refresh", existingCred.getRefreshToken());
    }

    @Test
    void logoutShouldRevokeRefreshToken() {
        var request = new AuthLogoutRequest("refresh-session-token");

        var response = authController.logout(request);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(refreshTokenService).revoke("refresh-session-token", "LOGOUT");
    }

    @Test
    void refreshShouldIssueAccessTokenFromDetachedPrincipal() {
        var request = new AuthRefreshRequest("refresh-token", null, null);
        var principal = new AuthenticatedUser(1L, "g-sub", "user@test.com", "ApplicationUser");
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
    void refreshShouldTranslateDataAccessFailuresToRetryableException() {
        var request = new AuthRefreshRequest("refresh-token", null, null);
        when(refreshTokenService.rotate(eq("refresh-token"), any()))
                .thenThrow(new RecoverableDataAccessException("database unavailable"));

        var ex = assertThrows(RefreshRetryableException.class, () -> authController.refresh(request));
        assertEquals("Refresh temporarily unavailable", ex.getMessage());
    }

    @Test
    void refreshShouldKeepTerminalStatusesAsRefreshTokenException() {
        var request = new AuthRefreshRequest("refresh-token", null, null);
        when(refreshTokenService.rotate(eq("refresh-token"), any()))
                .thenReturn(RefreshTokenService.RotationResult.expired());

        var ex = assertThrows(RefreshTokenException.class, () -> authController.refresh(request));
        assertEquals("REFRESH_TOKEN_EXPIRED", ex.getCode());
    }

    private void stubSessionIssuance(ApplicationUser user) {
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

    private static GoogleOAuthClient.AuthorizationCodeExchangeResult exchangeResult(
            String accessToken,
            String refreshToken,
            long expiresInSeconds
    ) {
        return new GoogleOAuthClient.AuthorizationCodeExchangeResult(
                "id-token",
                accessToken,
                refreshToken,
                expiresInSeconds
        );
    }
}
