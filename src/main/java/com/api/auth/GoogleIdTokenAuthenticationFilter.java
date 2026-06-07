package com.api.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class GoogleIdTokenAuthenticationFilter extends OncePerRequestFilter {
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleIdTokenAuthenticationFilter.class);

    private final AccessTokenService accessTokens;

    public GoogleIdTokenAuthenticationFilter(
            final AccessTokenService accessTokens
    ) {
        super();
        this.accessTokens = accessTokens;
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader(AUTH_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            final String token = authHeader.substring(BEARER_PREFIX.length());
            final AccessTokenService.AccessTokenValidationResult accessValidation = accessTokens.validate(token);
            if (accessValidation.isValid() && accessValidation.principal() != null) {
                final UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        accessValidation.principal(), null, Collections.emptyList()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                logValidationResult("valid", request);
                filterChain.doFilter(request, response);
                return;
            }
            logValidationResult(accessValidation.status().name(), request);
        }

        filterChain.doFilter(request, response);
    }

    private static void logValidationResult(final String validationResult, final HttpServletRequest request) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "auth_access_token_validation result={} method={} path={}",
                    validationResult,
                    request.getMethod(),
                    request.getRequestURI()
            );
        }
    }
}
