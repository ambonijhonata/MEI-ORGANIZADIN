package com.api.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshSessionTokenRepository extends JpaRepository<RefreshSessionToken, UUID> {
    Optional<RefreshSessionToken> findByTokenHash(String tokenHash);

    @Query("""
            select t from RefreshSessionToken t
            where t.user.id = :userId
            and t.revokedAt is null
            and t.expiresAt > :now
            """)
    List<RefreshSessionToken> findActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("""
            update RefreshSessionToken t
            set t.revokedAt = :revokedAt, t.revokedReason = :reason
            where t.user.id = :userId and t.revokedAt is null
            """)
    int revokeAllActiveByUserId(
            @Param("userId") Long userId,
            @Param("revokedAt") Instant revokedAt,
            @Param("reason") String reason
    );
}
