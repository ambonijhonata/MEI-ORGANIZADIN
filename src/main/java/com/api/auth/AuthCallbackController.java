package com.api.auth;

import com.api.google.GoogleOAuthClient;
import com.api.google.GoogleOAuthProperties;
import com.api.user.User;
import com.api.user.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação (Teste)", description = "Endpoints auxiliares para testar o fluxo OAuth via navegador")
public class AuthCallbackController {

    private final GoogleOAuthClient googleOAuthClient;
    private final GoogleOAuthProperties googleOAuthProperties;
    private final GoogleIdTokenValidator tokenValidator;
    private final UserRepository userRepository;
    private final OAuthCredentialRepository oauthCredentialRepository;

    public AuthCallbackController(GoogleOAuthClient googleOAuthClient,
                                   GoogleOAuthProperties googleOAuthProperties,
                                   GoogleIdTokenValidator tokenValidator,
                                   UserRepository userRepository,
                                   OAuthCredentialRepository oauthCredentialRepository) {
        this.googleOAuthClient = googleOAuthClient;
        this.googleOAuthProperties = googleOAuthProperties;
        this.tokenValidator = tokenValidator;
        this.userRepository = userRepository;
        this.oauthCredentialRepository = oauthCredentialRepository;
    }

    @GetMapping(value = "/google", produces = "text/html")
    @Operation(summary = "Iniciar login Google (navegador)",
            description = "Redireciona o navegador para a tela de login do Google. " +
                    "Após autorizar, o Google redireciona para /api/auth/callback com o code.")
    public String redirectToGoogle(HttpServletRequest request) {
        String redirectUri = getRedirectUri(request);
        String authUrl = googleOAuthProperties.authUri()
                + "?client_id=" + googleOAuthProperties.clientId()
                + "&redirect_uri=" + redirectUri
                + "&response_type=code"
                + "&scope=openid%20email%20profile%20https://www.googleapis.com/auth/calendar.readonly"
                + "&access_type=offline"
                + "&prompt=consent";
        return "<!DOCTYPE html><html><body>"
                + "<h2>mei-organizadin - Login de Teste</h2>"
                + "<p><a href=\"" + authUrl + "\">Clique aqui para fazer login com Google</a></p>"
                + "</body></html>";
    }

    @GetMapping("/callback")
    @Operation(summary = "Callback OAuth Google",
            description = "Recebe o authorization code do Google após o redirect, " +
                    "troca por tokens, cria/atualiza o usuário e retorna os dados. " +
                    "Use o idToken retornado como Bearer token nos outros endpoints.")
    public Map<String, Object> callback(
            @RequestParam("code") String code,
            @RequestParam(value = "error", required = false) String error,
            HttpServletRequest request) {

        if (error != null) {
            return Map.of("error", error, "message", "Google retornou erro na autorização");
        }

        String redirectUri = getRedirectUri(request);

        try {
            GoogleTokenResponse tokenResponse = googleOAuthClient.exchangeAuthorizationCode(code, redirectUri);

            String idTokenString = tokenResponse.getIdToken();
            String accessToken = tokenResponse.getAccessToken();
            String refreshToken = tokenResponse.getRefreshToken();

            GoogleIdToken.Payload payload = tokenValidator.validate(idTokenString)
                    .orElseThrow(() -> new AuthController.InvalidTokenException("ID Token inválido após troca"));

            String googleSub = payload.getSubject();
            String email = payload.getEmail();
            String rawName = (String) payload.get("name");
            final String name = rawName != null ? rawName : email;

            User user = userRepository.findByGoogleSub(googleSub)
                    .map(existing -> {
                        existing.setEmail(email);
                        existing.setName(name);
                        return userRepository.save(existing);
                    })
                    .orElseGet(() -> userRepository.save(new User(googleSub, email, name)));

            Instant expiresAt = Instant.now().plusSeconds(tokenResponse.getExpiresInSeconds());
            OAuthCredential credential = oauthCredentialRepository.findByUserId(user.getId())
                    .map(existing -> {
                        existing.setAccessToken(accessToken);
                        existing.setRefreshToken(refreshToken);
                        existing.setExpiresAt(expiresAt);
                        return existing;
                    })
                    .orElse(new OAuthCredential(user, accessToken, refreshToken, expiresAt));
            oauthCredentialRepository.save(credential);

            return Map.of(
                    "message", "Login realizado com sucesso!",
                    "userId", user.getId(),
                    "email", user.getEmail(),
                    "name", user.getName(),
                    "idToken", idTokenString,
                    "instrucao", "Use o idToken acima como Bearer token no header Authorization dos outros endpoints"
            );

        } catch (IOException e) {
            return Map.of("error", "OAUTH_EXCHANGE_FAILED", "message", e.getMessage());
        }
    }

    private String getRedirectUri(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String portStr = (port == 80 || port == 443) ? "" : ":" + port;
        return scheme + "://" + host + portStr + "/api/auth/callback";
    }
}
