package com.api.calendar;

import com.api.IntegrationTestBase;
import com.api.auth.AuthenticatedUser;
import com.api.client.Client;
import com.api.client.ClientRepository;
import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceRepository;
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
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
class ManualAppointmentIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private CalendarEventRepository calendarEventRepository;

    @BeforeEach
    void cleanDatabase() {
        calendarEventRepository.deleteAll();
        serviceRepository.deleteAll();
        clientRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void shouldCreateManualAppointmentAndExposeItInCalendarListing() throws Exception {
        User user = userRepository.save(new User("sub-manual", "manual@test.com", "Manual"));
        Client client = clientRepository.save(new Client(user, "Maria", "maria"));
        Service corte = serviceRepository.save(new Service(user, "Corte", "corte", new BigDecimal("50.00")));
        Service escova = serviceRepository.save(new Service(user, "Escova", "escova", new BigDecimal("80.00")));

        mockMvc.perform(post("/api/calendar/events")
                        .with(auth(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateManualAppointmentBody(
                                client.getId(),
                                "2026-05-27",
                                "14:00",
                                "14:30",
                                List.of(corte.getId(), escova.getId())
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.googleEventId").value(nullValue()))
                .andExpect(jsonPath("$.title").value("Maria - Corte + Escova"))
                .andExpect(jsonPath("$.identified").value(true))
                .andExpect(jsonPath("$.serviceDescription").value("Corte"))
                .andExpect(jsonPath("$.serviceValue").value(130.00))
                .andExpect(jsonPath("$.eventStart").value("2026-05-27T17:00:00Z"))
                .andExpect(jsonPath("$.eventEnd").value("2026-05-27T17:30:00Z"));

        List<CalendarEvent> persisted = calendarEventRepository.findByUserId(user.getId(), org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        assertThat(persisted).hasSize(1);
        assertThat(persisted.getFirst().getSource()).isEqualTo(CalendarEventSource.MANUAL);
        assertThat(persisted.getFirst().getGoogleEventId()).isNull();

        mockMvc.perform(get("/api/calendar/events")
                        .with(auth(user))
                        .queryParam("eventStart", "2026-05-27")
                        .queryParam("eventEnd", "2026-05-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Maria - Corte + Escova"))
                .andExpect(jsonPath("$.content[0].serviceValue").value(130.00));
    }

    @Test
    void shouldRejectManualAppointmentWithForeignService() throws Exception {
        User owner = userRepository.save(new User("sub-owner", "owner@test.com", "Owner"));
        User otherUser = userRepository.save(new User("sub-other", "other@test.com", "Other"));
        Client client = clientRepository.save(new Client(owner, "Maria", "maria"));
        Service foreignService = serviceRepository.save(new Service(otherUser, "Escova", "escova", new BigDecimal("80.00")));

        mockMvc.perform(post("/api/calendar/events")
                        .with(auth(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateManualAppointmentBody(
                                client.getId(),
                                "2026-05-27",
                                "14:00",
                                "14:30",
                                List.of(foreignService.getId())
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Service not found"));
    }

    private RequestPostProcessor auth(User user) {
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getGoogleSub(), user.getEmail(), user.getName());
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }

    private record CreateManualAppointmentBody(
            Long clientId,
            String appointmentDate,
            String startTime,
            String endTime,
            List<Long> serviceIds
    ) {
    }
}
