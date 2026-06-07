package com.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class AccessTokenService {
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final SessionTokenProperties properties;
    private final SecretKey signingKey;

    public AccessTokenService(final SessionTokenProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedAccessToken issue(final AuthenticatedUser user) {
        final Instant issuedAt = Instant.now();
        final Instant expiresAt = issuedAt.plus(properties.accessTtl());
        final String token = Jwts.builder()
                .subject(String.valueOf(user.userId()))
                .claim("typ", "access")
                .claim("uid", user.userId())
                .claim("gsub", user.googleSub())
                .claim("email", user.email())
                .claim("name", user.name())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedAccessToken(token, expiresAt);
    }

    public AccessTokenValidationResult validate(final String token) {
        final ValidationState state = new ValidationState();
        try {
            state.setResult(validateToken(token));
        } catch (ExpiredJwtException ex) {
            state.setResult(AccessTokenValidationResult.expired());
        } catch (JwtException | IllegalArgumentException ex) {
            state.setResult(AccessTokenValidationResult.invalid(ex.getMessage()));
        }
        return state.getResult();
    }

    private AccessTokenValidationResult validateToken(final String token) {
        return resolveValidationResult(parseClaims(token));
    }

    private Claims parseClaims(final String token) {
        final Jws<Claims> parsedClaims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
        return parsedClaims.getPayload();
    }

    private AccessTokenValidationResult resolveValidationResult(final Claims claims) {
        AccessTokenValidationResult result = AccessTokenValidationResult.invalid("unsupported_type");
        final String tokenType = claims.get("typ", String.class);
        if (isAccessTokenType(tokenType)) {
            final Long userId = claims.get("uid", Long.class);
            final String googleSub = claims.get("gsub", String.class);
            final String email = claims.get("email", String.class);
            final String name = claims.get("name", String.class);
            result = AccessTokenValidationResult.invalid("missing_claims");
            if (userId != null && googleSub != null && email != null && name != null) {
                result = AccessTokenValidationResult.valid(
                        new AuthenticatedUser(userId, googleSub, email, name)
                );
            }
        }
        return result;
    }

    private boolean isAccessTokenType(final String tokenType) {
        return ACCESS_TOKEN_TYPE.equals(tokenType);
    }

    private static final class ValidationState {
        private AccessTokenValidationResult result;

        private AccessTokenValidationResult getResult() {
            return result;
        }

        private void setResult(final AccessTokenValidationResult result) {
            this.result = result;
        }
    }

    public record IssuedAccessToken(
            String token,
            Instant expiresAt
    ) {
    }

    public record AccessTokenValidationResult(
            TokenStatus status,
            AuthenticatedUser principal,
            String reason
    ) {
        /* package */ static AccessTokenValidationResult valid(final AuthenticatedUser principal) {
            return new AccessTokenValidationResult(TokenStatus.VALID, principal, null);
        }

        /* package */ static AccessTokenValidationResult invalid(final String reason) {
            return new AccessTokenValidationResult(TokenStatus.INVALID, null, reason);
        }

        /* package */ static AccessTokenValidationResult expired() {
            return new AccessTokenValidationResult(TokenStatus.EXPIRED, null, "expired");
        }
    }

    public enum TokenStatus {
        VALID,
        INVALID,
        EXPIRED
    }
}
