package com.api.auth;

import com.api.user.ApplicationUser;
import com.api.user.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserResolver {

    private final UserRepository userRepository;

    public AuthenticatedUserResolver(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AuthenticatedUser resolve(final GoogleUserProfile profile) {
        return resolveUser(profile).toAuthenticatedUser();
    }

    public ApplicationUser resolveUser(final GoogleUserProfile profile) {
        final String googleSub = profile.googleSub();
        final String email = profile.email();
        final String name = profile.name();

        return userRepository.findByGoogleSub(googleSub)
                .map(existing -> {
                    existing.updateProfile(email, name);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(new ApplicationUser(googleSub, email, name)));
    }
}
