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

    private final GoogleIdTokenVerifier verifier;

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

    public GoogleIdTokenValidator(final GoogleOAuthProperties properties) {
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance()
        )
                .setAudience(Collections.singletonList(properties.clientId()))
                .build();
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
                result = ValidationResult.valid(extractProfile(idToken));
            }
        } catch (IOException | ReflectiveOperationException e) {
            result = ValidationResult.unavailable(e);
        } catch (GeneralSecurityException e) {
            result = ValidationResult.invalid(e);
        }
        return result;
    }

    private static GoogleUserProfile extractProfile(final GoogleIdToken idToken)
            throws ReflectiveOperationException {
        final Object payload = idToken.getClass().getMethod("getPayload").invoke(idToken);
        final String email = invokeString(payload, "getEmail");
        final Object nameValue = payload.getClass().getMethod("get", String.class).invoke(payload, "name");
        final String name = nameValue instanceof String value ? value : email;
        final String subject = invokeString(payload, "getSubject");
        return new GoogleUserProfile(subject, email, name);
    }

    private static String invokeString(final Object target, final String methodName)
            throws ReflectiveOperationException {
        return (String) target.getClass().getMethod(methodName).invoke(target);
    }
}
