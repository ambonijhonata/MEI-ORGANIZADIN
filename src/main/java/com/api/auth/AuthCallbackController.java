package com.api.auth;

import com.api.google.GoogleOAuthClient;
import com.api.google.GoogleOAuthProperties;
import com.api.user.User;
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

@SuppressWarnings({"PMD.LongVariable", "PMD.LooseCoupling", "PMD.OnlyOneReturn"})
@RestController
@RequestMapping("/api/auth")
@Tag(name = "AutenticaÃ§Ã£o (Teste)", description = "Endpoints auxiliares para testar o fluxo OAuth via navegador")
public class AuthCallbackController {

    private final GoogleOAuthClient googleOAuthClient;
    private final GoogleOAuthProperties googleOAuthProperties;
    private final GoogleIdTokenValidator tokenValidator;
    private final AuthenticatedUserResolver userResolver;
    private final OAuthCredentialRepository oauthCredentialRepository;

    public AuthCallbackController(final GoogleOAuthClient googleOAuthClient,
                                   final GoogleOAuthProperties googleOAuthProperties,
                                   final GoogleIdTokenValidator tokenValidator,
                                   final AuthenticatedUserResolver userResolver,
                                   final OAuthCredentialRepository oauthCredentialRepository) {
        this.googleOAuthClient = googleOAuthClient;
        this.googleOAuthProperties = googleOAuthProperties;
        this.tokenValidator = tokenValidator;
        this.userResolver = userResolver;
        this.oauthCredentialRepository = oauthCredentialRepository;
    }

    @GetMapping(value = "/google", produces = "text/html")
    @Operation(summary = "Iniciar login Google (navegador)",
            description = "Redireciona o navegador para a tela de login do Google. " +
                    "ApÃ³s autorizar, o Google redireciona para /api/auth/callback com o code.")
    public String redirectToGoogle(final HttpServletRequest request) {
        final String redirectUri = getRedirectUri(request);
        final String authUrl = googleOAuthProperties.authUri()
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
            description = "Recebe o authorization code do Google apÃ³s o redirect, " +
                    "troca por tokens, cria/atualiza o usuÃ¡rio e retorna os dados. " +
                    "Use o idToken retornado como Bearer token nos outros endpoints.")
    public Map<String, Object> callback(
            @RequestParam("code") final String code,
            @RequestParam(value = "error", required = false) final String error,
            final HttpServletRequest request) {

        if (error != null) {
            return Map.of("error", error, "message", "Google retornou erro na autorizaÃ§Ã£o");
        }

        final String redirectUri = getRedirectUri(request);

        try {
            final GoogleTokenResponse tokenResponse = googleOAuthClient.exchangeAuthorizationCode(code, redirectUri);

            final String idTokenString = tokenResponse.getIdToken();
            final String accessToken = tokenResponse.getAccessToken();
            final String refreshToken = tokenResponse.getRefreshToken();

            final GoogleIdToken.Payload payload = tokenValidator.validate(idTokenString)
                    .orElseThrow(() -> new AuthController.InvalidTokenException("ID Token invÃ¡lido apÃ³s troca"));

            final User user = userResolver.resolveUser(payload);

            final Instant expiresAt = Instant.now().plusSeconds(tokenResponse.getExpiresInSeconds());
            final OAuthCredential credential = oauthCredentialRepository.findByUserId(user.getId())
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

    private String getRedirectUri(final HttpServletRequest request) {
        final String scheme = request.getScheme();
        final String host = request.getServerName();
        final int port = request.getServerPort();
        final String portStr = (port == 80 || port == 443) ? "" : ":" + port;
        return scheme + "://" + host + portStr + "/api/auth/callback";
    }
}
