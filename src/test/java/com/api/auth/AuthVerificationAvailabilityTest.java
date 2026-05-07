package com.api.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TestProtectedController.class)
@Import({SecurityConfig.class, GoogleIdTokenAuthenticationFilter.class})
class AuthVerificationAvailabilityTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AccessTokenService accessTokenService;

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
        when(accessTokenService.validate(anyString()))
                .thenReturn(AccessTokenService.AccessTokenValidationResult.invalid("invalid"));

        mockMvc.perform(get("/api/test/protected").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void authenticatedInternalFailureShouldReturn500AndNotUnauthorized() throws Exception {
        when(accessTokenService.validate(anyString()))
                .thenReturn(AccessTokenService.AccessTokenValidationResult.valid(
                        new AuthenticatedUser(1L, "sub", "test@example.com", "Test")
                ));

        mockMvc.perform(get("/api/test/protected")
                        .param("fail", "true")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
    }

}
