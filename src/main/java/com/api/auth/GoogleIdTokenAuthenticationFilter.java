package com.api.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
public class GoogleIdTokenAuthenticationFilter extends OncePerRequestFilter {

    private final GoogleIdTokenValidator tokenValidator;
    private final AuthenticatedUserResolver userResolver;

    public GoogleIdTokenAuthenticationFilter(GoogleIdTokenValidator tokenValidator,
                                              AuthenticatedUserResolver userResolver) {
        this.tokenValidator = tokenValidator;
        this.userResolver = userResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Optional<GoogleIdToken.Payload> payload = tokenValidator.validate(token);

            if (payload.isPresent()) {
                AuthenticatedUser authenticatedUser = userResolver.resolve(payload.get());
                var authentication = new UsernamePasswordAuthenticationToken(
                        authenticatedUser, null, Collections.emptyList()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
