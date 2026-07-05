package com.api.auth;

import com.api.calendar.SyncOperationalState;
import com.api.calendar.SyncStateRepository;
import com.api.google.GoogleOAuthClient;
import com.api.user.User;
import com.api.user.UserRepository;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuthAccountService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthAccountService.class);

    private final UserRepository userRepository;
    private final GoogleOAuthClient googleClient;
    private final OAuthCredentialRepository credentialRepo;
    private final SyncStateRepository syncRepo;

    public AuthAccountService(
            final UserRepository userRepository,
            final GoogleOAuthClient googleClient,
            final OAuthCredentialRepository credentialRepo,
            final SyncStateRepository syncRepo
    ) {
        this.userRepository = userRepository;
        this.googleClient = googleClient;
        this.credentialRepo = credentialRepo;
        this.syncRepo = syncRepo;
    }

    public User upsertUser(final GoogleUserProfile googleUser) {
        return userRepository.findByGoogleSub(googleUser.googleSub())
                .map(existing -> {
                    existing.setEmail(googleUser.email());
                    existing.setName(googleUser.name());
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(
                        new User(googleUser.googleSub(), googleUser.email(), googleUser.name())
                ));
    }

    public void persistGoogleOAuthCredentialIfPresent(final String authorizationCode, final User user) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            logMissingAuthorizationCode();
            return;
        }
        try {
            final GoogleOAuthClient.AuthorizationCodeExchangeResult tokenResponse =
                    googleClient.exchangeAuthorizationCodeResult(authorizationCode, "");

            final Instant expiresAt = Instant.now().plusSeconds(tokenResponse.expiresInSeconds());
            final OAuthCredential credential = credentialRepo.findByUserId(user.getId())
                    .map(existing -> {
                        existing.updateTokens(
                                tokenResponse.accessToken(),
                                tokenResponse.refreshToken(),
                                expiresAt
                        );
                        return existing;
                    })
                    .orElse(new OAuthCredential(
                            user,
                            tokenResponse.accessToken(),
                            tokenResponse.refreshToken(),
                            expiresAt
                    ));
            credentialRepo.save(credential);
        } catch (IOException e) {
            logOAuthExchangeFailure();
            throw new OAuthExchangeException("OAuth exchange failed", e);
        }
    }

    public void clearReauthRequiredIfPresent(final Long userId) {
        syncRepo.findByUserId(userId)
                .filter(state -> state.operationalState().isReauthRequired())
                .ifPresent(state -> {
                    final SyncOperationalState.SyncStateSnapshot snapshot = state.operationalState().snapshot();
                    state.operationalState().keepSyncedWithExistingToken(snapshot.syncToken(), snapshot.lastSyncAt());
                    syncRepo.save(state);
                });
    }

    private static void logMissingAuthorizationCode() {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("auth_login_oauth_code status=missing");
        }
    }

    private static void logOAuthExchangeFailure() {
        if (LOGGER.isWarnEnabled()) {
            LOGGER.warn("auth_login_oauth_code status=exchange_failed");
        }
    }
}
