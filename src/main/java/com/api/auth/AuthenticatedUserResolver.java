package com.api.auth;

import com.api.user.User;
import com.api.user.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.springframework.stereotype.Component;

@SuppressWarnings("PMD.LooseCoupling")
@Component
public class AuthenticatedUserResolver {

    private final UserRepository userRepository;

    public AuthenticatedUserResolver(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AuthenticatedUser resolve(final GoogleIdToken.Payload payload) {
        final User user = resolveUser(payload);
        return new AuthenticatedUser(user.getId(), user.getGoogleSub(), user.getEmail(), user.getName());
    }

    public User resolveUser(final GoogleIdToken.Payload payload) {
        final String googleSub = payload.getSubject();
        final String email = payload.getEmail();
        final String rawName = (String) payload.get("name");
        final String name = rawName != null ? rawName : email;

        return userRepository.findByGoogleSub(googleSub)
                .map(existing -> {
                    existing.setEmail(email);
                    existing.setName(name);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(new User(googleSub, email, name)));
    }
}
