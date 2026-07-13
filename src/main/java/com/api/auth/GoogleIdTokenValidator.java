package com.api.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.api.google.GoogleOAuthProperties;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Component
public class GoogleIdTokenValidator {

    private static final String NAME_FIELD = "name";
    private static final int JWT_PAYLOAD_INDEX = 1;
    private final GoogleIdTokenVerifier verifier;
    private final ObjectMapper objectMapper;

    public enum Status {
        VALID,
        INVALID,
        UNAVAILABLE
    }

    public record ValidationResult(
            Status status,
            GoogleUserProfile profile,
            Exception exception
    ) {
        public static ValidationResult valid(final GoogleUserProfile profile) {
            return new ValidationResult(Status.VALID, profile, null);
        }

        public static ValidationResult invalid(final Exception exception) {
            return new ValidationResult(Status.INVALID, null, exception);
        }

        public static ValidationResult unavailable(final Exception exception) {
            return new ValidationResult(Status.UNAVAILABLE, null, exception);
        }
    }

    public GoogleIdTokenValidator(final GoogleOAuthProperties properties, final ObjectMapper objectMapper) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(List.of(properties.clientId()))
                .build();
        this.objectMapper = objectMapper;
    }

    public Optional<GoogleUserProfile> validateProfile(final String idTokenString) {
        final ValidationResult result = validateDetailed(idTokenString);
        return Optional.ofNullable(result.profile());
    }

    public ValidationResult validateDetailed(final String idTokenString) {
        ValidationResult result;
        try {
            final GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                result = ValidationResult.invalid(null);
            } else {
                result = ValidationResult.valid(extractProfile(idTokenString));
            }
        } catch (IOException e) {
            result = ValidationResult.unavailable(e);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            result = ValidationResult.invalid(e);
        }
        return result;
    }

    private GoogleUserProfile extractProfile(final String idTokenString) {
        final JsonNode payload = readPayload(idTokenString);
        final String email = payload.path("email").asText();
        return new GoogleUserProfile(
                payload.path("sub").asText(),
                email,
                resolveName(payload.path(NAME_FIELD).textValue(), email)
        );
    }

    private JsonNode readPayload(final String idTokenString) {
        final String[] jwtSegments = idTokenString.split("\\.");
        if (jwtSegments.length <= JWT_PAYLOAD_INDEX) {
            throw new IllegalArgumentException("Invalid Google ID token payload");
        }

        try {
            final byte[] payloadBytes = Base64.getUrlDecoder().decode(jwtSegments[JWT_PAYLOAD_INDEX]);
            return objectMapper.readTree(new String(payloadBytes, StandardCharsets.UTF_8));
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid Google ID token payload", e);
        }
    }

    private static String resolveName(final String nameValue, final String email) {
        return nameValue == null || nameValue.isBlank() ? email : nameValue;
    }
}
