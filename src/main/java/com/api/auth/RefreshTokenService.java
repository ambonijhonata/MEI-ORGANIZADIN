package com.api.auth;

import com.api.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
    private final RefreshSessionTokenRepository repository;
    private final SessionTokenProperties properties;

    public RefreshTokenService(
            RefreshSessionTokenRepository repository,
            SessionTokenProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public IssuedRefreshToken issueForUser(User user, RefreshTokenMetadata metadata) {
        String plainToken = newRefreshToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(properties.getRefreshTokenTtlSeconds());
        RefreshSessionToken entity = RefreshSessionToken.issue(
                user,
                hashToken(plainToken),
                now,
                expiresAt,
                metadata != null ? metadata : RefreshTokenMetadata.empty()
        );
        repository.save(entity);
        return new IssuedRefreshToken(plainToken, expiresAt, entity.getId(), user);
    }

    @Transactional
    public RotationResult rotate(String refreshToken, RefreshTokenMetadata metadata) {
        Instant now = Instant.now();
        Optional<RefreshSessionToken> existingOpt = repository.findByTokenHash(hashToken(refreshToken));
        if (existingOpt.isEmpty()) {
            return RotationResult.invalid();
        }

        RefreshSessionToken current = existingOpt.get();
        if (current.isExpired(now)) {
            current.revoke("EXPIRED", now);
            repository.save(current);
            return RotationResult.expired();
        }
        if (current.isRevoked()) {
            if (current.isReplaced()) {
                revokeAllActiveUserTokens(current.getUser().getId(), "REUSE_DETECTED", now);
                return RotationResult.reused();
            }
            return RotationResult.revoked();
        }

        String newRawToken = newRefreshToken();
        Instant newExpiresAt = now.plusSeconds(properties.getRefreshTokenTtlSeconds());
        RefreshSessionToken replacement = RefreshSessionToken.issue(
                current.getUser(),
                hashToken(newRawToken),
                now,
                newExpiresAt,
                metadata != null ? metadata : RefreshTokenMetadata.empty()
        );
        repository.save(replacement);
        current.markReplacedBy(replacement.getId(), now);
        repository.save(current);

        return RotationResult.success(
                new IssuedRefreshToken(newRawToken, newExpiresAt, replacement.getId(), current.getUser())
        );
    }

    @Transactional
    public RevokeResult revoke(String refreshToken, String reason) {
        Optional<RefreshSessionToken> existing = repository.findByTokenHash(hashToken(refreshToken));
        if (existing.isEmpty()) {
            return RevokeResult.NOT_FOUND;
        }
        RefreshSessionToken token = existing.get();
        token.revoke(reason != null ? reason : "LOGOUT", Instant.now());
        repository.save(token);
        return RevokeResult.REVOKED;
    }

    @Transactional
    public void revokeAllActiveUserTokens(Long userId, String reason, Instant now) {
        List<RefreshSessionToken> activeTokens = repository.findActiveByUserId(userId, now);
        activeTokens.forEach(token -> token.revoke(reason, now));
        repository.saveAll(activeTokens);
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private String newRefreshToken() {
        byte[] randomBytes = new byte[48];
        ThreadLocalRandom.current().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public record IssuedRefreshToken(
            String token,
            Instant expiresAt,
            UUID tokenId,
            User user
    ) {
    }

    public record RotationResult(
            RotationStatus status,
            IssuedRefreshToken issuedToken
    ) {
        static RotationResult success(IssuedRefreshToken token) {
            return new RotationResult(RotationStatus.SUCCESS, token);
        }

        static RotationResult invalid() {
            return new RotationResult(RotationStatus.INVALID, null);
        }

        static RotationResult revoked() {
            return new RotationResult(RotationStatus.REVOKED, null);
        }

        static RotationResult reused() {
            return new RotationResult(RotationStatus.REUSED, null);
        }

        static RotationResult expired() {
            return new RotationResult(RotationStatus.EXPIRED, null);
        }
    }

    public enum RotationStatus {
        SUCCESS,
        INVALID,
        REVOKED,
        REUSED,
        EXPIRED
    }

    public enum RevokeResult {
        REVOKED,
        NOT_FOUND
    }
}
