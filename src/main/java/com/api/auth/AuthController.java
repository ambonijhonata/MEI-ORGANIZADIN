package com.api.auth;

import com.api.calendar.SyncStateRepository;
import com.api.calendar.SyncStatus;
import com.api.google.GoogleOAuthClient;
import com.api.user.User;
import com.api.user.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Login inicial com Google ID Token e authorization code")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final GoogleIdTokenValidator tokenValidator;
    private final UserRepository userRepository;
    private final GoogleOAuthClient googleOAuthClient;
    private final OAuthCredentialRepository oauthCredentialRepository;
    private final SyncStateRepository syncStateRepository;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
            GoogleIdTokenValidator tokenValidator,
            UserRepository userRepository,
            GoogleOAuthClient googleOAuthClient,
            OAuthCredentialRepository oauthCredentialRepository,
            SyncStateRepository syncStateRepository,
            AccessTokenService accessTokenService,
            RefreshTokenService refreshTokenService
    ) {
        this.tokenValidator = tokenValidator;
        this.userRepository = userRepository;
        this.googleOAuthClient = googleOAuthClient;
        this.oauthCredentialRepository = oauthCredentialRepository;
        this.syncStateRepository = syncStateRepository;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Login inicial",
            description = "Recebe o Google ID Token e authorization code do app Android. " +
                    "Valida o token, cria/atualiza o usuário, troca o authorization code por tokens OAuth e emite sessão própria.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login realizado com sucesso"),
                    @ApiResponse(responseCode = "401", description = "Google ID Token inválido"),
                    @ApiResponse(responseCode = "502", description = "Falha na troca do authorization code com o Google")
            }
    )
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        GoogleIdToken.Payload payload = tokenValidator.validate(request.idToken())
                .orElseThrow(() -> new InvalidTokenException("Invalid Google ID Token"));

        User user = upsertUser(payload);
        persistGoogleOAuthCredentialIfPresent(request.authorizationCode(), user);
        clearReauthRequiredIfPresent(user.getId());

        AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(),
                user.getGoogleSub(),
                user.getEmail(),
                user.getName()
        );
        AccessTokenService.IssuedAccessToken issuedAccessToken = accessTokenService.issue(principal);
        RefreshTokenService.IssuedRefreshToken issuedRefreshToken = refreshTokenService.issueForUser(
                user,
                request.metadataOrEmpty()
        );

        return ResponseEntity.ok(
                new LoginResponse(
                        user.getId(),
                        user.getEmail(),
                        user.getName(),
                        issuedAccessToken.token(),
                        issuedAccessToken.expiresAt(),
                        issuedRefreshToken.token(),
                        issuedRefreshToken.expiresAt()
                )
        );
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Renovar sessão",
            description = "Rotaciona refresh token e devolve novo access token e refresh token.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Sessão renovada"),
                    @ApiResponse(responseCode = "401", description = "Refresh token inválido/revogado/expirado/reutilizado")
            }
    )
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        final RefreshTokenService.RotationResult rotation;
        try {
            rotation = refreshTokenService.rotate(
                    request.refreshToken(),
                    request.metadataOrEmpty()
            );
        } catch (RuntimeException ex) {
            log.warn("auth_refresh_result status=RETRYABLE_FAILURE message={}", ex.getMessage());
            throw new RefreshRetryableException("Refresh temporarily unavailable");
        }

        if (!rotation.isSuccessful() || rotation.issuedToken() == null || rotation.issuedToken().principal() == null) {
            throw RefreshTokenException.fromStatus(rotation.status());
        }
        log.info(
                "auth_refresh_result status={} retrySafe={}",
                rotation.status(),
                rotation.retrySafe()
        );

        AuthenticatedUser principal = rotation.issuedToken().principal();
        AccessTokenService.IssuedAccessToken accessToken = accessTokenService.issue(principal);
        return ResponseEntity.ok(
                new RefreshResponse(
                        accessToken.token(),
                        accessToken.expiresAt(),
                        rotation.issuedToken().token(),
                        rotation.issuedToken().expiresAt()
                )
        );
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Encerrar sessão",
            description = "Revoga o refresh token da sessão ativa."
    )
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken(), "LOGOUT");
        return ResponseEntity.noContent().build();
    }

    private User upsertUser(GoogleIdToken.Payload payload) {
        String googleSub = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        if (name == null) {
            name = email;
        }

        final String finalName = name;
        return userRepository.findByGoogleSub(googleSub)
                .map(existing -> {
                    existing.setEmail(email);
                    existing.setName(finalName);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(new User(googleSub, email, finalName)));
    }

    private void persistGoogleOAuthCredentialIfPresent(String authorizationCode, User user) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            log.info("Login without authorizationCode for userId={}", user.getId());
            return;
        }
        try {
            GoogleTokenResponse tokenResponse = googleOAuthClient.exchangeAuthorizationCode(authorizationCode);

            Instant expiresAt = Instant.now().plusSeconds(tokenResponse.getExpiresInSeconds());
            OAuthCredential credential = oauthCredentialRepository.findByUserId(user.getId())
                    .map(existing -> {
                        existing.setAccessToken(tokenResponse.getAccessToken());
                        existing.setRefreshToken(tokenResponse.getRefreshToken());
                        existing.setExpiresAt(expiresAt);
                        return existing;
                    })
                    .orElse(new OAuthCredential(
                            user,
                            tokenResponse.getAccessToken(),
                            tokenResponse.getRefreshToken(),
                            expiresAt
                    ));
            oauthCredentialRepository.save(credential);
        } catch (IOException e) {
            log.warn("OAuth code exchange failed for userId={}: {}", user.getId(), e.getMessage());
            throw new OAuthExchangeException("OAuth exchange failed: " + e.getMessage());
        }
    }

    private void clearReauthRequiredIfPresent(Long userId) {
        syncStateRepository.findByUserId(userId)
                .filter(state -> state.getStatus() == SyncStatus.REAUTH_REQUIRED)
                .ifPresent(state -> {
                    state.setStatus(SyncStatus.SYNCED);
                    state.setErrorCategory(null);
                    state.setErrorMessage(null);
                    syncStateRepository.save(state);
                });
    }

    public record LoginRequest(
            @NotBlank String idToken,
            String authorizationCode,
            String deviceId,
            String appVersion
    ) {
        RefreshTokenMetadata metadataOrEmpty() {
            return new RefreshTokenMetadata(deviceId, appVersion, null, null);
        }
    }

    public record RefreshRequest(
            @NotBlank String refreshToken,
            String deviceId,
            String appVersion
    ) {
        RefreshTokenMetadata metadataOrEmpty() {
            return new RefreshTokenMetadata(deviceId, appVersion, null, null);
        }
    }

    public record LogoutRequest(@NotBlank String refreshToken) {}

    public record LoginResponse(
            Long userId,
            String email,
            String name,
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt
    ) {
    }

    public record RefreshResponse(
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt
    ) {
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }

    public static class OAuthExchangeException extends RuntimeException {
        public OAuthExchangeException(String message) {
            super(message);
        }
    }

    public static class RefreshTokenException extends RuntimeException {
        private final String code;

        private RefreshTokenException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        static RefreshTokenException fromStatus(RefreshTokenService.RotationStatus status) {
            return switch (status) {
                case INVALID -> new RefreshTokenException("REFRESH_TOKEN_INVALID", "Refresh token is invalid");
                case REVOKED -> new RefreshTokenException("REFRESH_TOKEN_REVOKED", "Refresh token is revoked");
                case REUSED -> new RefreshTokenException("REFRESH_TOKEN_REUSED", "Refresh token reuse detected");
                case EXPIRED -> new RefreshTokenException("REFRESH_TOKEN_EXPIRED", "Refresh token is expired");
                case SUCCESS, RETRY_SAFE_SUCCESS -> new RefreshTokenException("REFRESH_TOKEN_INVALID", "Refresh token is invalid");
                default -> new RefreshTokenException("REFRESH_TOKEN_INVALID", "Refresh token is invalid");
            };
        }
    }

    public static class RefreshRetryableException extends RuntimeException {
        public RefreshRetryableException(String message) {
            super(message);
        }
    }
}
