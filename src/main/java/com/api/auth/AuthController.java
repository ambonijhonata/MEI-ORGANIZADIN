package com.api.auth;

import com.api.user.ApplicationUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "AutenticaÃ§Ã£o", description = "Login inicial com Google ID Token e authorization code")
public class AuthController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    private final GoogleIdTokenValidator tokenValidator;
    private final AuthAccountService accountSvc;
    private final AccessTokenService accessTokens;
    private final RefreshTokenService refreshTokens;

    public AuthController(
            final GoogleIdTokenValidator tokenValidator,
            final AuthAccountService accountSvc,
            final AccessTokenService accessTokens,
            final RefreshTokenService refreshTokens
    ) {
        this.tokenValidator = tokenValidator;
        this.accountSvc = accountSvc;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Login inicial",
            description = "Recebe o Google ID Token e authorization code do app Android. " +
                    "Valida o token, cria/atualiza o usuÃ¡rio, troca o authorization code por tokens OAuth e emite sessÃ£o prÃ³pria.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login realizado com sucesso"),
                    @ApiResponse(responseCode = "401", description = "Google ID Token invÃ¡lido"),
                    @ApiResponse(responseCode = "502", description = "Falha na troca do authorization code com o Google")
            }
    )
    public ResponseEntity<AuthLoginResponse> login(@Valid @RequestBody final AuthLoginRequest request) {
        final GoogleUserProfile googleUser = tokenValidator.validateProfile(request.idToken())
                .orElseThrow(() -> new InvalidTokenException("Invalid Google ID Token"));

        final ApplicationUser user = accountSvc.upsertUser(googleUser);
        accountSvc.persistGoogleOAuthCredentialIfPresent(request.authorizationCode(), user);
        accountSvc.clearReauthRequiredIfPresent(user.getId());

        final AuthenticatedUser principal = new AuthenticatedUser(
                user.getId(),
                user.getGoogleSub(),
                user.getEmail(),
                user.getName()
        );
        final AccessTokenService.IssuedAccessToken accessToken = accessTokens.issue(principal);
        final RefreshTokenService.IssuedRefreshToken refreshToken = refreshTokens.issueForUser(
                user,
                request.metadataOrEmpty()
        );

        return ResponseEntity.ok(
                new AuthLoginResponse(
                        user.getId(),
                        user.getEmail(),
                        user.getName(),
                        accessToken.token(),
                        accessToken.expiresAt(),
                        refreshToken.token(),
                        refreshToken.expiresAt()
                )
        );
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Renovar sessÃ£o",
            description = "Rotaciona refresh token e devolve novo access token e refresh token.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "SessÃ£o renovada"),
                    @ApiResponse(responseCode = "401", description = "Refresh token invÃ¡lido/revogado/expirado/reutilizado")
            }
    )
    public ResponseEntity<AuthRefreshResponse> refresh(@Valid @RequestBody final AuthRefreshRequest request) {
        final RefreshTokenService.RotationResult rotation;
        try {
            rotation = refreshTokens.rotate(
                    request.refreshToken(),
                    request.metadataOrEmpty()
            );
        } catch (DataAccessException ex) {
            logRefreshRetryableFailure();
            throw new RefreshRetryableException("Refresh temporarily unavailable", ex);
        }

        if (!rotation.isSuccessful() || rotation.issuedToken() == null || rotation.issuedToken().principal() == null) {
            throw RefreshTokenException.fromStatus(rotation.status());
        }
        logRefreshResult(rotation);

        final AuthenticatedUser principal = rotation.issuedToken().principal();
        final AccessTokenService.IssuedAccessToken accessToken = accessTokens.issue(principal);
        return ResponseEntity.ok(
                new AuthRefreshResponse(
                        accessToken.token(),
                        accessToken.expiresAt(),
                        rotation.issuedToken().token(),
                        rotation.issuedToken().expiresAt()
                )
        );
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Encerrar sessÃ£o",
            description = "Revoga o refresh token da sessÃ£o ativa."
    )
    public ResponseEntity<Void> logout(@Valid @RequestBody final AuthLogoutRequest request) {
        refreshTokens.revoke(request.refreshToken(), "LOGOUT");
        return ResponseEntity.noContent().build();
    }

    private static void logRefreshResult(final RefreshTokenService.RotationResult rotation) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(
                    "auth_refresh_result status={} retrySafe={}",
                    rotation.status(),
                    rotation.retrySafe()
            );
        }
    }

    private static void logRefreshRetryableFailure() {
        if (LOGGER.isWarnEnabled()) {
            LOGGER.warn("auth_refresh_result status=RETRYABLE_FAILURE");
        }
    }

}
