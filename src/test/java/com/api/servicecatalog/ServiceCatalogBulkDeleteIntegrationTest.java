package com.api.servicecatalog;

import com.api.IntegrationTestBase;
import com.api.auth.AuthenticatedUser;
import com.api.calendar.CalendarEvent;
import com.api.calendar.CalendarEventRepository;
import com.api.user.User;
import com.api.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
class ServiceCatalogBulkDeleteIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private CalendarEventRepository calendarEventRepository;

    @BeforeEach
    void cleanDatabase() {
        calendarEventRepository.deleteAll();
        serviceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldDeleteAllUnlinkedServices() throws Exception {
        User user = userRepository.save(new User("sub-delete-all", "all@test.com", "All"));
        Service first = serviceRepository.save(new Service(user, "Corte", "corte", new BigDecimal("50.00")));
        Service second = serviceRepository.save(new Service(user, "Escova", "escova", new BigDecimal("80.00")));

        mockMvc.perform(post("/api/services/delete")
                        .with(auth(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(first.getId(), second.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(2))
                .andExpect(jsonPath("$.hasLink").value(0));
    }

    @Test
    void shouldReturnMixedCountersWhenSomeServicesAreLinked() throws Exception {
        User user = userRepository.save(new User("sub-mixed", "mixed@test.com", "Mixed"));
        Service linked = serviceRepository.save(new Service(user, "Coloracao", "coloracao", new BigDecimal("120.00")));
        Service unlinked = serviceRepository.save(new Service(user, "Lavagem", "lavagem", new BigDecimal("40.00")));

        CalendarEvent event = new CalendarEvent(
                user,
                "evt-1",
                "Coloracao",
                "coloracao",
                Instant.parse("2026-03-10T10:00:00Z"),
                Instant.parse("2026-03-10T11:00:00Z")
        );
        event.associateService(linked);
        calendarEventRepository.save(event);

        mockMvc.perform(post("/api/services/delete")
                        .with(auth(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(linked.getId(), unlinked.getId(), 999999L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(1))
                .andExpect(jsonPath("$.hasLink").value(1));
    }

    private RequestPostProcessor auth(User user) {
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getGoogleSub(), user.getEmail(), user.getName());
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }
}
