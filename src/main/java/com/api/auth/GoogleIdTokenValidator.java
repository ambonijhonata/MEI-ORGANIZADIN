package com.api.auth;

import com.api.google.GoogleOAuthProperties;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Optional;

@Component
public class GoogleIdTokenValidator {

    public enum Status {
        VALID,
        INVALID,
        UNAVAILABLE
    }

    public record ValidationResult(
            Status status,
            GoogleIdToken.Payload payload,
            Exception exception
    ) {
        public static ValidationResult valid(GoogleIdToken.Payload payload) {
            return new ValidationResult(Status.VALID, payload, null);
        }

        public static ValidationResult invalid(Exception exception) {
            return new ValidationResult(Status.INVALID, null, exception);
        }

        public static ValidationResult unavailable(Exception exception) {
            return new ValidationResult(Status.UNAVAILABLE, null, exception);
        }
    }

    private final GoogleIdTokenVerifier verifier;

    public GoogleIdTokenValidator(GoogleOAuthProperties properties) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(Collections.singletonList(properties.clientId()))
                .build();
    }

    public Optional<GoogleIdToken.Payload> validate(String idTokenString) {
        ValidationResult result = validateDetailed(idTokenString);
        return Optional.ofNullable(result.payload());
    }

    public ValidationResult validateDetailed(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                return ValidationResult.invalid(null);
            }
            return ValidationResult.valid(idToken.getPayload());
        } catch (IOException e) {
            return ValidationResult.unavailable(e);
        } catch (GeneralSecurityException e) {
            return ValidationResult.invalid(e);
        }
    }
}
