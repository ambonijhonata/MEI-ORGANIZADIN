package com.api.auth;

import com.api.user.User;
import com.api.user.UserRepository;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserResolver {

    private final UserRepository userRepository;

    public AuthenticatedUserResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AuthenticatedUser resolve(GoogleIdToken.Payload payload) {
        String googleSub = payload.getSubject();
        String email = payload.getEmail();
        String rawName = (String) payload.get("name");
        final String name = rawName != null ? rawName : email;

        User user = userRepository.findByGoogleSub(googleSub)
                .map(existing -> {
                    existing.setEmail(email);
                    existing.setName(name);
                    return userRepository.save(existing);
                })
                .orElseGet(() -> userRepository.save(new User(googleSub, email, name)));

        return new AuthenticatedUser(user.getId(), user.getGoogleSub(), user.getEmail(), user.getName());
    }
}
