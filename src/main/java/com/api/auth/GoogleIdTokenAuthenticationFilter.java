package com.api.auth;
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

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenAuthenticationFilter.class);

    private final AccessTokenService accessTokenService;

    public GoogleIdTokenAuthenticationFilter(
            AccessTokenService accessTokenService
    ) {
        this.accessTokenService = accessTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            AccessTokenService.AccessTokenValidationResult accessValidation = accessTokenService.validate(token);
            if (accessValidation.status() == AccessTokenService.TokenStatus.VALID && accessValidation.principal() != null) {
                var authentication = new UsernamePasswordAuthenticationToken(
                        accessValidation.principal(), null, Collections.emptyList()
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("auth_access_token_validation result=valid method={} path={}", request.getMethod(), request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }
            log.debug("auth_access_token_validation result={} method={} path={}",
                    accessValidation.status(), request.getMethod(), request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}
