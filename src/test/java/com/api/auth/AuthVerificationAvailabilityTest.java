package com.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthVerificationAvailabilityTest.ProtectedTestController.class)
@Import({SecurityConfig.class, GoogleIdTokenAuthenticationFilter.class})
class AuthVerificationAvailabilityTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private GoogleIdTokenValidator tokenValidator;
    @MockBean private AuthenticatedUserResolver userResolver;

    @Test
    void missingTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidTokenShouldReturn401() throws Exception {
        when(tokenValidator.validateDetailed(anyString()))
                .thenReturn(GoogleIdTokenValidator.ValidationResult.invalid(new RuntimeException("invalid")));

        mockMvc.perform(get("/api/test/protected").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void verificationUnavailableShouldReturn503() throws Exception {
        when(tokenValidator.validateDetailed(anyString()))
                .thenReturn(GoogleIdTokenValidator.ValidationResult.unavailable(new IOException("keys fetch failed")));

        mockMvc.perform(get("/api/test/protected").header("Authorization", "Bearer any"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("AUTH_VERIFICATION_UNAVAILABLE"));
    }

    @RestController
    @RequestMapping("/api/test")
    static class ProtectedTestController {
        @GetMapping("/protected")
        String protectedEndpoint() {
            return "ok";
        }
    }
}

