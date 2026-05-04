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

@Service
public class RefreshTokenService {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int MAX_RETRY_SAFE_CHAIN_HOPS = 4;

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
        return new IssuedRefreshToken(
                plainToken,
                expiresAt,
                entity.getId(),
                new AuthenticatedUser(user.getId(), user.getGoogleSub(), user.getEmail(), user.getName())
        );
    }

    @Transactional
    public RotationResult rotate(String refreshToken, RefreshTokenMetadata metadata) {
        Instant now = Instant.now();
        Optional<RefreshSessionToken> existingOpt = repository.findByTokenHash(hashToken(refreshToken));
        if (existingOpt.isEmpty()) {
            log.warn("refresh_rotation_outcome outcome=invalid reason=token_not_found");
            return RotationResult.invalid();
        }

        RefreshSessionToken current = existingOpt.get();
        if (current.isExpired(now)) {
            current.revoke("EXPIRED", now);
            repository.save(current);
            log.warn("refresh_rotation_outcome outcome=expired userId={} tokenId={}", current.getUser().getId(), current.getId());
            return RotationResult.expired();
        }
        if (current.isRevoked()) {
            if (current.isReplaced()) {
                if (isRetrySafeCandidate(current, metadata, now)) {
                    Optional<RefreshSessionToken> retrySafeBase = resolveRetrySafeBaseToken(current, now);
                    if (retrySafeBase.isPresent()) {
                        log.info(
                                "refresh_rotation_outcome outcome=retry_safe_deduped userId={} sourceTokenId={} baseTokenId={}",
                                current.getUser().getId(),
                                current.getId(),
                                retrySafeBase.get().getId()
                        );
                        return rotateFromActiveToken(
                                retrySafeBase.get(),
                                metadata != null ? metadata : RefreshTokenMetadata.empty(),
                                now,
                                true
                        );
                    }
                }
                revokeAllActiveUserTokens(current.getUser().getId(), "REUSE_DETECTED", now);
                log.warn("refresh_rotation_outcome outcome=reused userId={} tokenId={}", current.getUser().getId(), current.getId());
                return RotationResult.reused();
            }
            log.warn("refresh_rotation_outcome outcome=revoked userId={} tokenId={}", current.getUser().getId(), current.getId());
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

    private RotationResult rotateFromActiveToken(
            RefreshSessionToken activeToken,
            RefreshTokenMetadata metadata,
            Instant now,
            boolean retrySafe
    ) {
        String newRawToken = newRefreshToken();
        Instant newExpiresAt = now.plusSeconds(properties.getRefreshTokenTtlSeconds());
        RefreshSessionToken replacement = RefreshSessionToken.issue(
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
            log.info(
                    "refresh_rotation_outcome outcome=retry_safe_rotated userId={} previousTokenId={} replacementTokenId={}",
                    activeToken.getUser().getId(),
                    activeToken.getId(),
                    replacement.getId()
            );
            return RotationResult.retrySafeSuccess(
                    new IssuedRefreshToken(newRawToken, newExpiresAt, replacement.getId(), toPrincipal(activeToken.getUser()))
            );
        }

        log.info(
                "refresh_rotation_outcome outcome=rotated userId={} previousTokenId={} replacementTokenId={}",
                activeToken.getUser().getId(),
                activeToken.getId(),
                replacement.getId()
        );
        return RotationResult.success(
                new IssuedRefreshToken(newRawToken, newExpiresAt, replacement.getId(), toPrincipal(activeToken.getUser()))
        );
    }

    private AuthenticatedUser toPrincipal(User user) {
        return new AuthenticatedUser(
                user.getId(),
                user.getGoogleSub(),
                user.getEmail(),
                user.getName()
        );
    }

    private boolean isRetrySafeCandidate(
            RefreshSessionToken rotatedToken,
            RefreshTokenMetadata requestMetadata,
            Instant now
    ) {
        if (!"ROTATED".equals(rotatedToken.getRevokedReason())) return false;
        if (rotatedToken.getRevokedAt() == null) return false;
        if (properties.getRefreshRetrySafetyWindowSeconds() <= 0) return false;
        Instant retryWindowLimit = rotatedToken
                .getRevokedAt()
                .plusSeconds(properties.getRefreshRetrySafetyWindowSeconds());
        if (now.isAfter(retryWindowLimit)) return false;
        return matchesRetryMetadata(rotatedToken, requestMetadata != null ? requestMetadata : RefreshTokenMetadata.empty());
    }

    private Optional<RefreshSessionToken> resolveRetrySafeBaseToken(
            RefreshSessionToken sourceToken,
            Instant now
    ) {
        UUID currentId = sourceToken.getReplacedByTokenId();
        int hops = 0;
        while (currentId != null && hops < MAX_RETRY_SAFE_CHAIN_HOPS) {
            Optional<RefreshSessionToken> candidateOpt = repository.findById(currentId);
            if (candidateOpt.isEmpty()) {
                return Optional.empty();
            }
            RefreshSessionToken candidate = candidateOpt.get();
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

    private boolean matchesRetryMetadata(RefreshSessionToken sourceToken, RefreshTokenMetadata metadata) {
        if (metadata == null) return true;
        return matchesIfProvided(metadata.deviceId(), sourceToken.getDeviceId()) &&
                matchesIfProvided(metadata.appVersion(), sourceToken.getAppVersion()) &&
                matchesIfProvided(metadata.createdIp(), sourceToken.getCreatedIp()) &&
                matchesIfProvided(metadata.createdUserAgent(), sourceToken.getCreatedUserAgent());
    }

    private boolean matchesIfProvided(String incoming, String stored) {
        if (incoming == null || incoming.isBlank()) return true;
        return Objects.equals(incoming, stored);
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
            AuthenticatedUser principal
    ) {
    }

    public record RotationResult(
            RotationStatus status,
            IssuedRefreshToken issuedToken,
            boolean retrySafe
    ) {
        static RotationResult success(IssuedRefreshToken token) {
            return new RotationResult(RotationStatus.SUCCESS, token, false);
        }

        static RotationResult retrySafeSuccess(IssuedRefreshToken token) {
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
