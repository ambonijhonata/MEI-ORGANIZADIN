package com.api.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.function.ThrowingSupplier;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final int STATUS_401 = HttpServletResponse.SC_UNAUTHORIZED;
    private static final String UNAUTHORIZED_CODE = "UNAUTHORIZED";
    private static final String UNAUTHORIZED_MSG =
            "Authentication required. Provide a valid access token in the Authorization header.";

    private final GoogleIdTokenAuthenticationFilter tokenFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            final GoogleIdTokenAuthenticationFilter tokenFilter,
            final ObjectMapper objectMapper
    ) {
        this.tokenFilter = tokenFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(final HttpSecurity http) {
        return ThrowingSupplier.of(
                () -> {
                    http
                        .csrf(AbstractHttpConfigurer::disable)
                        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                        .authorizeHttpRequests(auth -> auth
                                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                                .requestMatchers(
                                        "/api/auth/login",
                                        "/api/auth/refresh",
                                        "/api/auth/logout",
                                        "/api/auth/google",
                                        "/api/auth/callback",
                                        "/healthz"
                                ).permitAll()
                                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                                .anyRequest().authenticated()
                        )
                        .exceptionHandling(ex -> ex.authenticationEntryPoint(unauthorizedEntryPoint()))
                        .addFilterBefore(tokenFilter, UsernamePasswordAuthenticationFilter.class);

                    return http.build();
                },
                SecurityConfig::newSecurityChainBuildException
        ).get();
    }

    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) -> writeUnauthorizedResponse(response);
    }

    private void writeUnauthorizedResponse(final HttpServletResponse response) throws IOException {
        response.setStatus(STATUS_401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        final Map<String, Object> body = Map.of(
                "status", STATUS_401,
                "code", UNAUTHORIZED_CODE,
                "message", UNAUTHORIZED_MSG,
                "timestamp", Instant.now().toString()
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static IllegalStateException newSecurityChainBuildException(
            final String unusedMessage,
            final Exception exception
    ) {
        return new IllegalStateException("Failed to build security filter chain", exception);
    }
}
