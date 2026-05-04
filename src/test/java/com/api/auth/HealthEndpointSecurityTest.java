package com.api.auth;

import com.api.health.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {HealthController.class, HealthEndpointSecurityTest.ProtectedTestController.class})
@Import({SecurityConfig.class, GoogleIdTokenAuthenticationFilter.class})
class HealthEndpointSecurityTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AccessTokenService accessTokenService;

    @Test
    void healthzShouldBePublic() throws Exception {
        mockMvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void protectedEndpointShouldStillRequireAuth() throws Exception {
        mockMvc.perform(get("/api/test/protected"))
                .andExpect(status().isUnauthorized());
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
