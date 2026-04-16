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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;

@Component
public class GoogleIdTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String ATTR_AUTH_VERIFICATION_UNAVAILABLE = "auth.verification.unavailable";

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenAuthenticationFilter.class);

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
            GoogleIdTokenValidator.ValidationResult validation = tokenValidator.validateDetailed(token);
            switch (validation.status()) {
                case VALID -> {
                    AuthenticatedUser authenticatedUser = userResolver.resolve(validation.payload());
                    var authentication = new UsernamePasswordAuthenticationToken(
                            authenticatedUser, null, Collections.emptyList()
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug("auth_id_token_validation result=valid method={} path={}", request.getMethod(), request.getRequestURI());
                }
                case INVALID -> {
                    log.debug("auth_id_token_validation result=invalid method={} path={}", request.getMethod(), request.getRequestURI());
                }
                case UNAVAILABLE -> {
                    request.setAttribute(ATTR_AUTH_VERIFICATION_UNAVAILABLE, Boolean.TRUE);
                    log.warn(
                            "auth_id_token_validation result=io_failure method={} path={} error={}",
                            request.getMethod(),
                            request.getRequestURI(),
                            validation.exception() != null ? validation.exception().getMessage() : null,
                            validation.exception()
                    );
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
