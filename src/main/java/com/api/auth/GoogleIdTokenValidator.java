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

@SuppressWarnings({"PMD.FieldDeclarationsShouldBeAtStartOfClass", "PMD.LooseCoupling", "PMD.OnlyOneReturn"})
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
        public static ValidationResult valid(final GoogleIdToken.Payload payload) {
            return new ValidationResult(Status.VALID, payload, null);
        }

        public static ValidationResult invalid(final Exception exception) {
            return new ValidationResult(Status.INVALID, null, exception);
        }

        public static ValidationResult unavailable(final Exception exception) {
            return new ValidationResult(Status.UNAVAILABLE, null, exception);
        }
    }

    private final GoogleIdTokenVerifier verifier;

    public GoogleIdTokenValidator(final GoogleOAuthProperties properties) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(Collections.singletonList(properties.clientId()))
                .build();
    }

    public Optional<GoogleIdToken.Payload> validate(final String idTokenString) {
        final ValidationResult result = validateDetailed(idTokenString);
        return Optional.ofNullable(result.payload());
    }

    public Optional<GoogleUserProfile> validateProfile(final String idTokenString) {
        return validate(idTokenString).map(payload -> {
            final String email = payload.getEmail();
            final String name = payload.get("name") instanceof String value ? value : email;
            return new GoogleUserProfile(payload.getSubject(), email, name);
        });
    }

    public ValidationResult validateDetailed(final String idTokenString) {
        try {
            final GoogleIdToken idToken = verifier.verify(idTokenString);
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
