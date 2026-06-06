package com.api.auth;

import com.api.user.User;
import com.api.user.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserResolver {

    private final UserRepository userRepository;

    public AuthenticatedUserResolver(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AuthenticatedUser resolve(final GoogleUserProfile profile) {
        final User user = resolveUser(profile);
        return new AuthenticatedUser(user.getId(), user.getGoogleSub(), user.getEmail(), user.getName());
    }

    public User resolveUser(final GoogleUserProfile profile) {
        final String googleSub = profile.googleSub();
        final String email = profile.email();
        final String name = profile.name();

        return userRepository.findByGoogleSub(googleSub)
                .map(existing -> {
                    existing.setEmail(email);
                    existing.setName(name);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(new User(googleSub, email, name)));
    }
}
