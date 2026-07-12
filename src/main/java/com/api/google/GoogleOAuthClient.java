package com.api.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GoogleOAuthClient {

    private final GoogleOAuthProperties properties;

    public record AuthorizationCodeExchangeResult(
            String idToken,
            String accessToken,
            String refreshToken,
            Long expiresInSeconds
    ) {
    }

    public GoogleOAuthClient(final GoogleOAuthProperties properties) {
        this.properties = properties;
    }

    public AuthorizationCodeExchangeResult exchangeAuthorizationCode(final String authorizationCode) throws IOException {
        return exchangeAuthorizationCode(authorizationCode, "");
    }

    public AuthorizationCodeExchangeResult exchangeAuthorizationCode(
            final String authorizationCode,
            final String redirectUri) throws IOException {
        return exchangeAuthorizationCodeResult(authorizationCode, redirectUri);
    }

    public AuthorizationCodeExchangeResult exchangeAuthorizationCodeResult(
            final String authorizationCode,
            final String redirectUri) throws IOException {
        return executeAuthorizationCodeExchange(authorizationCode, redirectUri);
    }

    public AuthorizationCodeExchangeResult refreshAccessToken(final String refreshToken) throws IOException {
        final Map<String, Object> tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                properties.tokenUri(),
                properties.clientId(),
                properties.clientSecret(),
                refreshToken,
                ""
        ).execute();
        return toAuthorizationCodeExchangeResult(tokenResponse);
    }

    private AuthorizationCodeExchangeResult executeAuthorizationCodeExchange(
            final String authorizationCode,
            final String redirectUri) throws IOException {
        final Map<String, Object> tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                properties.tokenUri(),
                properties.clientId(),
                properties.clientSecret(),
                authorizationCode,
                redirectUri
        ).execute();
        return toAuthorizationCodeExchangeResult(tokenResponse);
    }

    private AuthorizationCodeExchangeResult toAuthorizationCodeExchangeResult(final Map<String, Object> tokenResponse) {
        return new AuthorizationCodeExchangeResult(
                readIdToken(tokenResponse),
                readStringValue(tokenResponse, "access_token"),
                readStringValue(tokenResponse, "refresh_token"),
                readLongValue(tokenResponse, "expires_in")
        );
    }

    private String readIdToken(final Map<String, Object> tokenResponse) {
        return String.valueOf(tokenResponse.get("id_token"));
    }

    private String readStringValue(final Map<String, Object> tokenResponse, final String key) {
        return String.valueOf(tokenResponse.get(key));
    }

    private Long readLongValue(final Map<String, Object> tokenResponse, final String key) {
        final Object value = tokenResponse.get(key);
        final Long normalizedValue;
        if (value instanceof Number number) {
            normalizedValue = number.longValue();
        } else {
            normalizedValue = value == null ? absentLongValue() : Long.valueOf(String.valueOf(value));
        }
        return normalizedValue;
    }

    private Long absentLongValue() {
        return null;
    }
}
