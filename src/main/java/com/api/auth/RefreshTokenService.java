package com.api.auth;

import com.api.user.ApplicationUser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final String EXPIRED_REASON = "EXPIRED";
    private static final String LOGOUT_REASON = "LOGOUT";
    private static final String REUSE_REASON = "REUSE_DETECTED";
    private static final String ROTATED_REASON = "ROTATED";

    private final RefreshSessionTokenRepository repository;
    private final SessionTokenProperties properties;
    private final RefreshTokenRetrySafety retrySafety;

    public RefreshTokenService(
            final RefreshSessionTokenRepository repository,
            final SessionTokenProperties properties
    ) {
        this.repository = repository;
        this.properties = properties;
        this.retrySafety = new RefreshTokenRetrySafety(repository, properties, ROTATED_REASON);
    }

    @Transactional
    public IssuedRefreshToken issueForUser(final ApplicationUser user, final RefreshTokenMetadata metadata) {
        final String plainToken = newRefreshToken();
        final Instant now = Instant.now();
        final Instant expiresAt = now.plus(properties.refreshTtl());
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
        final RefreshTokenMetadata requestMetadata = metadata == null ? RefreshTokenMetadata.empty() : metadata;
        final Instant now = Instant.now();
        final Optional<RefreshSessionToken> tokenOpt = repository.findByTokenHash(hashToken(refreshToken));
        final RotationResult result;
        if (tokenOpt.isEmpty()) {
            LOGGER.warn("refresh_rotation_outcome outcome=invalid reason=token_not_found");
            result = RotationResult.invalid();
        } else {
            result = rotateExistingToken(tokenOpt.get(), requestMetadata, now);
        }
        return result;
    }

    @Transactional
    public RevokeResult revoke(final String refreshToken, final String reason) {
        final Optional<RefreshSessionToken> tokenOpt = repository.findByTokenHash(hashToken(refreshToken));
        final RevokeResult result;
        if (tokenOpt.isEmpty()) {
            result = RevokeResult.NOT_FOUND;
        } else {
            final RefreshSessionToken token = tokenOpt.get();
            final String revokeReason = reason == null ? LOGOUT_REASON : reason;
            token.revoke(revokeReason, Instant.now());
            repository.save(token);
            result = RevokeResult.REVOKED;
        }
        return result;
    }

    private RotationResult rotateExistingToken(
            final RefreshSessionToken currentToken,
            final RefreshTokenMetadata requestMetadata,
            final Instant now
    ) {
        final RotationResult result;
        if (currentToken.isExpired(now)) {
            currentToken.revoke(EXPIRED_REASON, now);
            repository.save(currentToken);
            LOGGER.warn("refresh_rotation_outcome outcome=expired");
            result = RotationResult.expired();
        } else if (currentToken.isRevoked()) {
            if (currentToken.isReplaced()) {
                result = handleReplacedToken(currentToken, requestMetadata, now);
            } else {
                LOGGER.warn("refresh_rotation_outcome outcome=revoked");
                result = RotationResult.revoked();
            }
        } else {
            result = rotateFromActiveToken(currentToken, requestMetadata, now, false);
        }
        return result;
    }

    private RotationResult handleReplacedToken(
            final RefreshSessionToken rotatedToken,
            final RefreshTokenMetadata requestMetadata,
            final Instant now
    ) {
        final Optional<RefreshSessionToken> retryBase =
                retrySafety.findRetryBase(rotatedToken, requestMetadata, now);
        final RotationResult result;
        if (retryBase.isPresent()) {
            LOGGER.info("refresh_rotation_outcome outcome=retry_safe_deduped");
            result = rotateFromActiveToken(retryBase.get(), requestMetadata, now, true);
        } else {
            repository.revokeAllActiveByUserId(rotatedToken.getUserId(), now, REUSE_REASON);
            LOGGER.warn("refresh_rotation_outcome outcome=reused");
            result = RotationResult.reused();
        }
        return result;
    }

    private RotationResult rotateFromActiveToken(
            final RefreshSessionToken activeToken,
            final RefreshTokenMetadata metadata,
            final Instant now,
            final boolean retrySafe
    ) {
        final ApplicationUser user = activeToken.getUser();
        final String plainToken = newRefreshToken();
        final Instant expiresAt = now.plus(properties.refreshTtl());
        final RefreshSessionToken replacement = RefreshSessionToken.issue(
                user,
                hashToken(plainToken),
                now,
                expiresAt,
                metadata
        );
        repository.save(replacement);
        activeToken.markReplacedBy(replacement.getId(), now);
        repository.save(activeToken);

        final RotationStatus status;
        final String outcome;
        if (retrySafe) {
            status = RotationStatus.RETRY_SAFE;
            outcome = "retry_safe_rotated";
        } else {
            status = RotationStatus.SUCCESS;
            outcome = "rotated";
        }
        LOGGER.info("refresh_rotation_outcome outcome={}", outcome);
        return new RotationResult(
                status,
                new IssuedRefreshToken(plainToken, expiresAt, replacement.getId(), activeToken.toAuthenticatedUser()),
                retrySafe
        );
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
        public static RotationResult success(final IssuedRefreshToken token) {
            return new RotationResult(RotationStatus.SUCCESS, token, false);
        }

        public static RotationResult retrySafeSuccess(final IssuedRefreshToken token) {
            return new RotationResult(RotationStatus.RETRY_SAFE, token, true);
        }

        public static RotationResult invalid() {
            return new RotationResult(RotationStatus.INVALID, null, false);
        }

        public static RotationResult revoked() {
            return new RotationResult(RotationStatus.REVOKED, null, false);
        }

        public static RotationResult reused() {
            return new RotationResult(RotationStatus.REUSED, null, false);
        }

        public static RotationResult expired() {
            return new RotationResult(RotationStatus.EXPIRED, null, false);
        }

        public boolean isSuccessful() {
            return status == RotationStatus.SUCCESS || status == RotationStatus.RETRY_SAFE;
        }
    }

    public enum RotationStatus {
        SUCCESS,
        RETRY_SAFE,
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
