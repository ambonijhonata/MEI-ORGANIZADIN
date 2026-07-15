package com.api.auth;

import com.api.user.ApplicationUser;
import com.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserResolverTest {

    @Mock
    private UserRepository userRepository;

    private AuthenticatedUserResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new AuthenticatedUserResolver(userRepository);
    }

    @Test
    void shouldCreateNewUserWhenNotFound() {
        GoogleUserProfile profile = new GoogleUserProfile("google-sub-123", "test@example.com", "Test ApplicationUser");

        ApplicationUser savedUser = new ApplicationUser("google-sub-123", "test@example.com", "Test ApplicationUser");
        when(userRepository.findByGoogleSub("google-sub-123")).thenReturn(Optional.empty());
        when(userRepository.save(any(ApplicationUser.class))).thenReturn(savedUser);

        AuthenticatedUser result = resolver.resolve(profile);

        assertEquals("google-sub-123", result.googleSub());
        assertEquals("test@example.com", result.email());
        assertEquals("Test ApplicationUser", result.name());
        verify(userRepository).save(any(ApplicationUser.class));
    }

    @Test
    void shouldUpdateExistingUser() {
        GoogleUserProfile profile = new GoogleUserProfile("google-sub-123", "newemail@example.com", "Updated Name");

        ApplicationUser existingUser = new ApplicationUser("google-sub-123", "old@example.com", "Old Name");
        when(userRepository.findByGoogleSub("google-sub-123")).thenReturn(Optional.of(existingUser));
        when(userRepository.save(existingUser)).thenReturn(existingUser);

        AuthenticatedUser result = resolver.resolve(profile);

        assertEquals("newemail@example.com", result.email());
        assertEquals("Updated Name", result.name());
    }

    @Test
    void shouldUseEmailAsNameWhenNameIsNull() {
        GoogleUserProfile profile = new GoogleUserProfile("google-sub-123", "test@example.com", "test@example.com");

        ApplicationUser savedUser = new ApplicationUser("google-sub-123", "test@example.com", "test@example.com");
        when(userRepository.findByGoogleSub("google-sub-123")).thenReturn(Optional.empty());
        when(userRepository.save(any(ApplicationUser.class))).thenReturn(savedUser);

        AuthenticatedUser result = resolver.resolve(profile);

        assertEquals("test@example.com", result.name());
    }
}
