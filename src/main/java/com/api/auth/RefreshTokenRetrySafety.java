package com.api.auth;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

final class RefreshTokenRetrySafety {
    private static final int MAX_RETRY_HOPS = 4;

    private final RefreshSessionTokenRepository repository;
    private final SessionTokenProperties properties;
    private final String rotatedReason;

    public RefreshTokenRetrySafety(
            final RefreshSessionTokenRepository repository,
            final SessionTokenProperties properties,
            final String rotatedReason
    ) {
        this.repository = repository;
        this.properties = properties;
        this.rotatedReason = rotatedReason;
    }

    public Optional<RefreshSessionToken> findRetryBase(
            final RefreshSessionToken rotatedToken,
            final RefreshTokenMetadata requestMetadata,
            final Instant now
    ) {
        Optional<RefreshSessionToken> retryBase = Optional.empty();
        if (isRetrySafeCandidate(rotatedToken, requestMetadata, now)) {
            retryBase = resolveRetryBase(rotatedToken.getReplacedByTokenId(), now);
        }
        return retryBase;
    }

    private boolean isRetrySafeCandidate(
            final RefreshSessionToken rotatedToken,
            final RefreshTokenMetadata requestMetadata,
            final Instant now
    ) {
        boolean retrySafe = rotatedToken.canRetrySafely(
                rotatedReason,
                now,
                properties.getRefreshRetrySafetyWindowSeconds()
        );
        if (retrySafe) {
            retrySafe = matchesRetryMetadata(rotatedToken, requestMetadata);
        }
        return retrySafe;
    }

    private Optional<RefreshSessionToken> resolveRetryBase(final UUID tokenId, final Instant now) {
        UUID currentId = tokenId;
        int hops = 0;
        Optional<RefreshSessionToken> retryBase = Optional.empty();
        while (currentId != null && hops < MAX_RETRY_HOPS && retryBase.isEmpty()) {
            final RetryBaseStep step = resolveRetryStep(currentId, now);
            retryBase = step.retryBase();
            currentId = step.nextId();
            hops += step.hopIncrement();
        }
        return retryBase;
    }

    private RetryBaseStep resolveRetryStep(final UUID tokenId, final Instant now) {
        final Optional<RefreshSessionToken> candidateOpt = repository.findById(tokenId);
        final RetryBaseStep step;
        if (candidateOpt.isEmpty()) {
            step = RetryBaseStep.stop();
        } else {
            step = resolveCandidate(candidateOpt.get(), now);
        }
        return step;
    }

    private RetryBaseStep resolveCandidate(final RefreshSessionToken candidate, final Instant now) {
        RetryBaseStep step = RetryBaseStep.stop();
        final boolean active = !candidate.isExpired(now) && !candidate.isRevoked();
        if (active) {
            step = RetryBaseStep.found(candidate);
        }
        final boolean replacedRevoked = !candidate.isExpired(now)
                && candidate.isRevoked()
                && candidate.isReplaced();
        if (replacedRevoked) {
            step = RetryBaseStep.follow(candidate.getReplacedByTokenId());
        }
        return step;
    }

    private boolean matchesRetryMetadata(
            final RefreshSessionToken sourceToken,
            final RefreshTokenMetadata metadata
    ) {
        return matchesIfProvided(metadata.deviceId(), sourceToken.getDeviceId())
                && matchesIfProvided(metadata.appVersion(), sourceToken.getAppVersion())
                && matchesIfProvided(metadata.createdIp(), sourceToken.getCreatedIp())
                && matchesIfProvided(metadata.createdUserAgent(), sourceToken.getCreatedUserAgent());
    }

    private boolean matchesIfProvided(final String incoming, final String stored) {
        boolean matches = true;
        if (incoming != null && !incoming.isBlank()) {
            matches = Objects.equals(incoming, stored);
        }
        return matches;
    }

    private record RetryBaseStep(
            Optional<RefreshSessionToken> retryBase,
            UUID nextId,
            int hopIncrement
    ) {
        private static RetryBaseStep stop() {
            return new RetryBaseStep(Optional.empty(), null, 0);
        }

        private static RetryBaseStep found(final RefreshSessionToken token) {
            return new RetryBaseStep(Optional.of(token), null, 0);
        }

        private static RetryBaseStep follow(final UUID nextId) {
            return new RetryBaseStep(Optional.empty(), nextId, 1);
        }
    }
}
