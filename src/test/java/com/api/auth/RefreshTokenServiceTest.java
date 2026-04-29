package com.api.auth;

import com.api.user.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {
    @Mock
    private RefreshSessionTokenRepository repository;

    private SessionTokenProperties properties;
    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        properties = new SessionTokenProperties();
        properties.setRefreshTokenTtlSeconds(3600);
        service = new RefreshTokenService(repository, properties);
    }

    @Test
    void shouldIssueAndRotateRefreshToken() {
        User user = new User("sub-1", "user@test.com", "User");
        RefreshTokenService.IssuedRefreshToken issued = service.issueForUser(user, RefreshTokenMetadata.empty());
        assertNotNull(issued.token());
        assertNotNull(issued.expiresAt());
        assertNotNull(issued.tokenId());

        String hash = service.hashToken(issued.token());
        RefreshSessionToken current = RefreshSessionToken.issue(
                user,
                hash,
                Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(3000),
                RefreshTokenMetadata.empty()
        );
        when(repository.findByTokenHash(hash)).thenReturn(Optional.of(current));
        when(repository.save(any(RefreshSessionToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.RotationResult rotation = service.rotate(issued.token(), RefreshTokenMetadata.empty());

        assertEquals(RefreshTokenService.RotationStatus.SUCCESS, rotation.status());
        assertNotNull(rotation.issuedToken());
        assertNotNull(current.getReplacedByTokenId());
        assertNotNull(current.getRevokedAt());
        assertEquals("ROTATED", current.getRevokedReason());
    }

    @Test
    void shouldRevokeAllActiveTokensWhenReusedTokenDetected() {
        User user = new User("sub-1", "user@test.com", "User");
        setUserId(user, 99L);
        String rawToken = "reused-token";
        String hash = service.hashToken(rawToken);
        RefreshSessionToken reused = RefreshSessionToken.issue(
                user,
                hash,
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                RefreshTokenMetadata.empty()
        );
        reused.markReplacedBy(java.util.UUID.randomUUID(), Instant.now().minusSeconds(1));

        when(repository.findByTokenHash(hash)).thenReturn(Optional.of(reused));
        when(repository.findActiveByUserId(eq(99L), any())).thenReturn(List.of());
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        RefreshTokenService.RotationResult rotation = service.rotate(rawToken, RefreshTokenMetadata.empty());

        assertEquals(RefreshTokenService.RotationStatus.REUSED, rotation.status());
        verify(repository).findActiveByUserId(eq(99L), any());
    }

    private void setUserId(User user, Long id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
