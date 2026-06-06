package com.api.auth;

import com.api.google.GoogleOAuthClient;
import com.api.google.GoogleOAuthProperties;
import com.api.user.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação (Teste)", description = "Endpoints auxiliares para testar o fluxo OAuth via navegador")
public class AuthCallbackController {

    private final GoogleOAuthClient googleOAuthClient;
    private final GoogleOAuthProperties oauthProps;
    private final GoogleIdTokenValidator tokenValidator;
    private final AuthenticatedUserResolver userResolver;
    private final OAuthCredentialRepository credentialRepo;

    public AuthCallbackController(final GoogleOAuthClient googleOAuthClient,
                                  final GoogleOAuthProperties oauthProps,
                                  final GoogleIdTokenValidator tokenValidator,
                                  final AuthenticatedUserResolver userResolver,
                                  final OAuthCredentialRepository credentialRepo) {
        this.googleOAuthClient = googleOAuthClient;
        this.oauthProps = oauthProps;
        this.tokenValidator = tokenValidator;
        this.userResolver = userResolver;
        this.credentialRepo = credentialRepo;
    }

    @GetMapping(value = "/google", produces = "text/html")
    @Operation(summary = "Iniciar login Google (navegador)",
            description = "Redireciona o navegador para a tela de login do Google. " +
                    "Após autorizar, o Google redireciona para /api/auth/callback com o code.")
    public String redirectToGoogle(final HttpServletRequest request) {
        final String redirectUri = getRedirectUri(request);
        final String authUrl = oauthProps.authUri()
                + "?client_id=" + oauthProps.clientId()
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
            @RequestParam("code") final String code,
            @RequestParam(value = "error", required = false) final String error,
            final HttpServletRequest request) {

        Map<String, Object> response;
        if (error != null) {
            response = Map.of("error", error, "message", "Google retornou erro na autorização");
        } else {
            final String redirectUri = getRedirectUri(request);

            try {
                final GoogleOAuthClient.AuthorizationCodeExchangeResult tokenResponse =
                        googleOAuthClient.exchangeAuthorizationCodeResult(code, redirectUri);
                final String idTokenString = tokenResponse.idToken();
                final String accessToken = tokenResponse.accessToken();
                final String refreshToken = tokenResponse.refreshToken();

                final User user = userResolver.resolveUser(
                        tokenValidator.validate(idTokenString)
                                .orElseThrow(() -> new AuthController.InvalidTokenException("ID Token inválido após troca"))
                );
                final Instant expiresAt = Instant.now().plusSeconds(tokenResponse.expiresInSeconds());
                final OAuthCredential credential = credentialRepo.findByUserId(user.getId())
                        .map(existing -> {
                            existing.setAccessToken(accessToken);
                            existing.setRefreshToken(refreshToken);
                            existing.setExpiresAt(expiresAt);
                            return existing;
                        })
                        .orElse(new OAuthCredential(user, accessToken, refreshToken, expiresAt));
                credentialRepo.save(credential);

                response = Map.of(
                        "message", "Login realizado com sucesso!",
                        "userId", user.getId(),
                        "email", user.getEmail(),
                        "name", user.getName(),
                        "idToken", idTokenString,
                        "instrucao", "Use o idToken acima como Bearer token no header Authorization dos outros endpoints"
                );
            } catch (IOException e) {
                response = Map.of("error", "OAUTH_EXCHANGE_FAILED", "message", e.getMessage());
            }
        }
        return response;
    }

    private String getRedirectUri(final HttpServletRequest request) {
        final String scheme = request.getScheme();
        final String host = request.getServerName();
        final int port = request.getServerPort();
        final String portStr = (port == 80 || port == 443) ? "" : ":" + port;
        return scheme + "://" + host + portStr + "/api/auth/callback";
    }
}
