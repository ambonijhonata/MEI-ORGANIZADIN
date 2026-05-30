package com.api.auth;

import com.api.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SuppressWarnings({"PMD.AvoidDeeplyNestedIfStmts", "PMD.CognitiveComplexity", "PMD.CommentDefaultAccessModifier", "PMD.ControlStatementBraces", "PMD.FieldNamingConventions", "PMD.LawOfDemeter", "PMD.LongVariable", "PMD.OnlyOneReturn", "PMD.SimplifyBooleanReturns", "PMD.TooManyMethods"})
@Service
public class RefreshTokenService {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int MAX_RETRY_SAFE_CHAIN_HOPS = 4;

    private final RefreshSessionTokenRepository repository;
    private final SessionTokenProperties properties;

    public RefreshTokenService(
            final RefreshSessionTokenRepository repository,
            final SessionTokenProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public IssuedRefreshToken issueForUser(final User user, final RefreshTokenMetadata metadata) {
        final String plainToken = newRefreshToken();
        final Instant now = Instant.now();
        final Instant expiresAt = now.plusSeconds(properties.getRefreshTokenTtlSeconds());
        final RefreshSessionToken entity = RefreshSessionToken.issue(
                user,
                hashToken(plainToken),
                now,
                expiresAt,
                metadata != null ? metadata : RefreshTokenMetadata.empty()
        );
        repository.save(entity);
        return new IssuedRefreshToken(
                plainToken,
                expiresAt,
                entity.getId(),
                new AuthenticatedUser(user.getId(), user.getGoogleSub(), user.getEmail(), user.getName())
        );
    }

    @Transactional
    public RotationResult rotate(final String refreshToken, final RefreshTokenMetadata metadata) {
        final Instant now = Instant.now();
        final Optional<RefreshSessionToken> existingOpt = repository.findByTokenHash(hashToken(refreshToken));
        if (existingOpt.isEmpty()) {
            log.warn("refresh_rotation_outcome outcome=invalid reason=token_not_found");
            return RotationResult.invalid();
        }

        final RefreshSessionToken current = existingOpt.get();
        if (current.isExpired(now)) {
            current.revoke("EXPIRED", now);
            repository.save(current);
            log.warn("refresh_rotation_outcome outcome=expired");
            return RotationResult.expired();
        }
        if (current.isRevoked()) {
            if (current.isReplaced()) {
                if (isRetrySafeCandidate(current, metadata, now)) {
                    final Optional<RefreshSessionToken> retrySafeBase = resolveRetrySafeBaseToken(current, now);
                    if (retrySafeBase.isPresent()) {
                        log.info("refresh_rotation_outcome outcome=retry_safe_deduped");
                        return rotateFromActiveToken(
                                retrySafeBase.get(),
                                metadata != null ? metadata : RefreshTokenMetadata.empty(),
                                now,
                                true
                        );
                    }
                }
                revokeAllActiveUserTokens(current.getUser().getId(), "REUSE_DETECTED", now);
                log.warn("refresh_rotation_outcome outcome=reused");
                return RotationResult.reused();
            }
            log.warn("refresh_rotation_outcome outcome=revoked");
            return RotationResult.revoked();
        }

        return rotateFromActiveToken(
                current,
                metadata != null ? metadata : RefreshTokenMetadata.empty(),
                now,
                false
        );
    }

    @Transactional
    public RevokeResult revoke(final String refreshToken, final String reason) {
        final Optional<RefreshSessionToken> existing = repository.findByTokenHash(hashToken(refreshToken));
        if (existing.isEmpty()) {
            return RevokeResult.NOT_FOUND;
        }
        final RefreshSessionToken token = existing.get();
        token.revoke(reason != null ? reason : "LOGOUT", Instant.now());
        repository.save(token);
        return RevokeResult.REVOKED;
    }

    @Transactional
    public void revokeAllActiveUserTokens(final Long userId, final String reason, final Instant now) {
        final List<RefreshSessionToken> activeTokens = repository.findActiveByUserId(userId, now);
        activeTokens.forEach(token -> token.revoke(reason, now));
        repository.saveAll(activeTokens);
    }

    private RotationResult rotateFromActiveToken(
            final RefreshSessionToken activeToken,
            final RefreshTokenMetadata metadata,
            final Instant now,
            final boolean retrySafe
    ) {
        final String newRawToken = newRefreshToken();
        final Instant newExpiresAt = now.plusSeconds(properties.getRefreshTokenTtlSeconds());
        final RefreshSessionToken replacement = RefreshSessionToken.issue(
                activeToken.getUser(),
                hashToken(newRawToken),
                now,
                newExpiresAt,
                metadata
        );
        repository.save(replacement);
        activeToken.markReplacedBy(replacement.getId(), now);
        repository.save(activeToken);

        if (retrySafe) {
            log.info("refresh_rotation_outcome outcome=retry_safe_rotated");
            return RotationResult.retrySafeSuccess(
                    new IssuedRefreshToken(newRawToken, newExpiresAt, replacement.getId(), toPrincipal(activeToken.getUser()))
            );
        }

        log.info("refresh_rotation_outcome outcome=rotated");
        return RotationResult.success(
                new IssuedRefreshToken(newRawToken, newExpiresAt, replacement.getId(), toPrincipal(activeToken.getUser()))
        );
    }

    private AuthenticatedUser toPrincipal(final User user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getGoogleSub(),
                user.getEmail(),
                user.getName()
        );
    }

    private boolean isRetrySafeCandidate(
            final RefreshSessionToken rotatedToken,
            final RefreshTokenMetadata requestMetadata,
            final Instant now
    ) {
        if (!"ROTATED".equals(rotatedToken.getRevokedReason())) return false;
        if (rotatedToken.getRevokedAt() == null) return false;
        if (properties.getRefreshRetrySafetyWindowSeconds() <= 0) return false;
        final Instant retryWindowLimit = rotatedToken
                .getRevokedAt()
                .plusSeconds(properties.getRefreshRetrySafetyWindowSeconds());
        if (now.isAfter(retryWindowLimit)) return false;
        return matchesRetryMetadata(rotatedToken, requestMetadata != null ? requestMetadata : RefreshTokenMetadata.empty());
    }

    private Optional<RefreshSessionToken> resolveRetrySafeBaseToken(
            final RefreshSessionToken sourceToken,
            final Instant now
    ) {
        UUID currentId = sourceToken.getReplacedByTokenId();
        int hops = 0;
        while (currentId != null && hops < MAX_RETRY_SAFE_CHAIN_HOPS) {
            final Optional<RefreshSessionToken> candidateOpt = repository.findById(currentId);
            if (candidateOpt.isEmpty()) {
                return Optional.empty();
            }
            final RefreshSessionToken candidate = candidateOpt.get();
            if (candidate.isExpired(now)) {
                return Optional.empty();
            }
            if (!candidate.isRevoked()) {
                return Optional.of(candidate);
            }
            if (!candidate.isReplaced()) {
                return Optional.empty();
            }
            currentId = candidate.getReplacedByTokenId();
            hops += 1;
        }
        return Optional.empty();
    }

    private boolean matchesRetryMetadata(final RefreshSessionToken sourceToken, final RefreshTokenMetadata metadata) {
        if (metadata == null) return true;
        return matchesIfProvided(metadata.deviceId(), sourceToken.getDeviceId()) &&
                matchesIfProvided(metadata.appVersion(), sourceToken.getAppVersion()) &&
                matchesIfProvided(metadata.createdIp(), sourceToken.getCreatedIp()) &&
                matchesIfProvided(metadata.createdUserAgent(), sourceToken.getCreatedUserAgent());
    }

    private boolean matchesIfProvided(final String incoming, final String stored) {
        if (incoming == null || incoming.isBlank()) return true;
        return Objects.equals(incoming, stored);
    }

    public String hashToken(final String token) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private String newRefreshToken() {
        final byte[] randomBytes = new byte[48];
        ThreadLocalRandom.current().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public record IssuedRefreshToken(
            String token,
            Instant expiresAt,
            UUID tokenId,
            AuthenticatedUser principal
    ) {
    }

    public record RotationResult(
            RotationStatus status,
            IssuedRefreshToken issuedToken,
            boolean retrySafe
    ) {
        static RotationResult success(final IssuedRefreshToken token) {
            return new RotationResult(RotationStatus.SUCCESS, token, false);
        }

        static RotationResult retrySafeSuccess(final IssuedRefreshToken token) {
            return new RotationResult(RotationStatus.RETRY_SAFE_SUCCESS, token, true);
        }

        static RotationResult invalid() {
            return new RotationResult(RotationStatus.INVALID, null, false);
        }

        static RotationResult revoked() {
            return new RotationResult(RotationStatus.REVOKED, null, false);
        }

        static RotationResult reused() {
            return new RotationResult(RotationStatus.REUSED, null, false);
        }

        static RotationResult expired() {
            return new RotationResult(RotationStatus.EXPIRED, null, false);
        }

        boolean isSuccessful() {
            return status == RotationStatus.SUCCESS || status == RotationStatus.RETRY_SAFE_SUCCESS;
        }
    }

    public enum RotationStatus {
        SUCCESS,
        RETRY_SAFE_SUCCESS,
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
