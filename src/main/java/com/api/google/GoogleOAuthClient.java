package com.api.google;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GoogleOAuthClient {

    private final GoogleOAuthProperties properties;

    public GoogleOAuthClient(GoogleOAuthProperties properties) {
        this.properties = properties;
    }

    public GoogleTokenResponse exchangeAuthorizationCode(String authorizationCode) throws IOException {
        return exchangeAuthorizationCode(authorizationCode, "");
    }

    public GoogleTokenResponse exchangeAuthorizationCode(String authorizationCode, String redirectUri) throws IOException {
        return new GoogleAuthorizationCodeTokenRequest(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                properties.tokenUri(),
                properties.clientId(),
                properties.clientSecret(),
                authorizationCode,
                redirectUri
        ).execute();
    }

    public GoogleTokenResponse refreshAccessToken(String refreshToken) throws IOException {
        return new GoogleAuthorizationCodeTokenRequest(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                properties.tokenUri(),
                properties.clientId(),
                properties.clientSecret(),
                refreshToken,
                ""
        ).execute();
    }
}
