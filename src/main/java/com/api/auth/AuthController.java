package com.api.auth;

import com.api.calendar.SyncState;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;

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

    public AuthController(GoogleIdTokenValidator tokenValidator,
                           UserRepository userRepository,
                           GoogleOAuthClient googleOAuthClient,
                           OAuthCredentialRepository oauthCredentialRepository,
                           SyncStateRepository syncStateRepository) {
        this.tokenValidator = tokenValidator;
        this.userRepository = userRepository;
        this.googleOAuthClient = googleOAuthClient;
        this.oauthCredentialRepository = oauthCredentialRepository;
        this.syncStateRepository = syncStateRepository;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Login inicial",
            description = "Recebe o Google ID Token e authorization code do app Android. " +
                    "Valida o token, cria/atualiza o usuário e troca o authorization code por tokens OAuth.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Login realizado com sucesso"),
                    @ApiResponse(responseCode = "401", description = "Google ID Token inválido"),
                    @ApiResponse(responseCode = "502", description = "Falha na troca do authorization code com o Google")
            }
    )
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        GoogleIdToken.Payload payload = tokenValidator.validate(request.idToken())
                .orElseThrow(() -> new InvalidTokenException("Invalid Google ID Token"));

        String googleSub = payload.getSubject();
        String email = payload.getEmail();
        String name = (String) payload.get("name");
        if (name == null) {
            name = email;
        }

        final String finalName = name;
        User user = userRepository.findByGoogleSub(googleSub)
                .map(existing -> {
                    existing.setEmail(email);
                    existing.setName(finalName);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(new User(googleSub, email, finalName)));

        if (request.authorizationCode() != null && !request.authorizationCode().isBlank()) {
            try {
                GoogleTokenResponse tokenResponse = googleOAuthClient.exchangeAuthorizationCode(request.authorizationCode());

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
        } else {
            log.info("Login without authorizationCode for userId={}", user.getId());
        }

        // Clear reauth-required status if re-authenticating
        syncStateRepository.findByUserId(user.getId())
                .filter(state -> state.getStatus() == SyncStatus.REAUTH_REQUIRED)
                .ifPresent(state -> {
                    state.setStatus(SyncStatus.SYNCED);
                    state.setErrorCategory(null);
                    state.setErrorMessage(null);
                    syncStateRepository.save(state);
                });

        return ResponseEntity.ok(new LoginResponse(user.getId(), user.getEmail(), user.getName()));
    }

    public record LoginRequest(
            @NotBlank String idToken,
            String authorizationCode
    ) {}

    public record LoginResponse(Long userId, String email, String name) {}

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) { super(message); }
    }

    public static class OAuthExchangeException extends RuntimeException {
        public OAuthExchangeException(String message) { super(message); }
    }
}
