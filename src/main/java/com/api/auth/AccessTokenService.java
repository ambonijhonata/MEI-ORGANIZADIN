package com.api.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@SuppressWarnings({"PMD.CommentDefaultAccessModifier", "PMD.LawOfDemeter", "PMD.OnlyOneReturn"})
@Service
public class AccessTokenService {
    private final SessionTokenProperties properties;
    private final SecretKey signingKey;

    public AccessTokenService(final SessionTokenProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedAccessToken issue(final AuthenticatedUser user) {
        final Instant issuedAt = Instant.now();
        final Instant expiresAt = issuedAt.plusSeconds(properties.getAccessTokenTtlSeconds());
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
        try {
            final Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            final String tokenType = claims.get("typ", String.class);
            if (!"access".equals(tokenType)) {
                return AccessTokenValidationResult.invalid("unsupported_type");
            }
            final Long userId = claims.get("uid", Long.class);
            final String googleSub = claims.get("gsub", String.class);
            final String email = claims.get("email", String.class);
            final String name = claims.get("name", String.class);
            if (userId == null || googleSub == null || email == null || name == null) {
                return AccessTokenValidationResult.invalid("missing_claims");
            }
            return AccessTokenValidationResult.valid(
                    new AuthenticatedUser(userId, googleSub, email, name)
            );
        } catch (ExpiredJwtException ex) {
            return AccessTokenValidationResult.expired();
        } catch (JwtException | IllegalArgumentException ex) {
            return AccessTokenValidationResult.invalid(ex.getMessage());
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
        static AccessTokenValidationResult valid(final AuthenticatedUser principal) {
            return new AccessTokenValidationResult(TokenStatus.VALID, principal, null);
        }

        static AccessTokenValidationResult invalid(final String reason) {
            return new AccessTokenValidationResult(TokenStatus.INVALID, null, reason);
        }

        static AccessTokenValidationResult expired() {
            return new AccessTokenValidationResult(TokenStatus.EXPIRED, null, "expired");
        }
    }

    public enum TokenStatus {
        VALID,
        INVALID,
        EXPIRED
    }
}
