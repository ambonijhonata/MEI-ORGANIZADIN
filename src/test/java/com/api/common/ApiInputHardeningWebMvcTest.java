package com.api.common;

import com.api.auth.AuthenticatedUser;
import com.api.auth.GoogleIdTokenAuthenticationFilter;
import com.api.auth.SecurityConfig;
import com.api.calendar.CalendarController;
import com.api.calendar.CalendarEvent;
import com.api.calendar.CalendarEventPaymentRepository;
import com.api.calendar.CalendarEventRepository;
import com.api.calendar.CalendarPaymentService;
import com.api.calendar.CalendarSyncService;
import com.api.calendar.SyncStateRepository;
import com.api.client.Client;
import com.api.client.ClientController;
import com.api.client.ClientService;
import com.api.servicecatalog.Service;
import com.api.servicecatalog.ServiceCatalogController;
import com.api.servicecatalog.ServiceCatalogService;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        ClientController.class,
        ServiceCatalogController.class,
        CalendarController.class
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ApiInputHardeningWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientService clientService;

    @MockBean
    private CalendarEventRepository clientCalendarEventRepository;

    @MockBean
    private ServiceDescriptionNormalizer normalizer;

    @MockBean
    private ServiceCatalogService serviceCatalogService;

    @MockBean
    private CalendarSyncService calendarSyncService;

    @MockBean
    private CalendarPaymentService calendarPaymentService;

    @MockBean
    private CalendarEventPaymentRepository calendarEventPaymentRepository;

    @MockBean
    private SyncStateRepository syncStateRepository;

    @MockBean
    private GoogleIdTokenAuthenticationFilter googleIdTokenAuthenticationFilter;

    @BeforeEach
    void forwardSecurityFilterChain() throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain filterChain = invocation.getArgument(2);
            filterChain.doFilter(request, response);
            return null;
        }).when(googleIdTokenAuthenticationFilter)
                .doFilter(any(ServletRequest.class), any(ServletResponse.class), any(FilterChain.class));
    }

    @Test
    void shouldRejectInvalidSortDirectionAndPaginationParametersWith400() throws Exception {
        mockMvc.perform(get("/api/clients")
                        .with(authenticatedUser())
                        .param("sortBy", "name;drop table clients"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("sortBy"));

        mockMvc.perform(get("/api/services")
                        .with(authenticatedUser())
                        .param("direction", "desc;drop"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("direction"));

        mockMvc.perform(get("/api/clients")
                        .with(authenticatedUser())
                        .param("pageIndex", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("pageIndex"));

        mockMvc.perform(get("/api/services")
                        .with(authenticatedUser())
                        .param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));

        mockMvc.perform(get("/api/calendar/events")
                        .with(authenticatedUser())
                        .param("sort", "eventStart;delete,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));

        mockMvc.perform(get("/api/calendar/events")
                        .with(authenticatedUser())
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void shouldKeepDocumentedSortingAndPaginationWorking() throws Exception {
        PageRequest clientPageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.ASC, "name"));
        Page<Client> clientPage = new PageImpl<>(List.of(createClient(1L, "Ana")), clientPageable, 1);
        when(clientService.listClientsPaginated(1L, null, 1, 25, clientPageable.getSort())).thenReturn(clientPage);

        PageRequest servicePageable = PageRequest.of(0, 25, Sort.by(Sort.Direction.ASC, "description"));
        Page<Service> servicePage = new PageImpl<>(List.of(createService(1L, "Design", new BigDecimal("65.00"))), servicePageable, 1);
        when(serviceCatalogService.listServices(1L, null, servicePageable)).thenReturn(servicePage);

        PageRequest calendarPageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.ASC, "eventStart"));
        when(clientCalendarEventRepository.findByUserId(1L, calendarPageable)).thenReturn(new PageImpl<>(List.of(), calendarPageable, 0));

        mockMvc.perform(get("/api/clients")
                        .with(authenticatedUser())
                        .param("sortBy", "name")
                        .param("direction", "asc")
                        .param("pageIndex", "1")
                        .param("itemsPerPage", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));

        mockMvc.perform(get("/api/services")
                        .with(authenticatedUser())
                        .param("sortBy", "description")
                        .param("direction", "asc")
                        .param("page", "0")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));

        mockMvc.perform(get("/api/calendar/events")
                        .with(authenticatedUser())
                        .param("sort", "eventStart,asc")
                        .param("page", "0")
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    private RequestPostProcessor authenticatedUser() {
        AuthenticatedUser principal = new AuthenticatedUser(1L, "sub", "test@example.com", "Test");
        Authentication authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        return SecurityMockMvcRequestPostProcessors.authentication(authentication);
    }

    private Client createClient(Long id, String name) {
        Client client = mock(Client.class);
        when(client.getId()).thenReturn(id);
        when(client.getName()).thenReturn(name);
        when(client.getCpf()).thenReturn("12345678900");
        when(client.getDateOfBirth()).thenReturn(LocalDate.of(1990, 1, 1));
        when(client.getEmail()).thenReturn(name.toLowerCase() + "@test.com");
        when(client.getPhone()).thenReturn("11999999999");
        when(client.getCreatedAt()).thenReturn(Instant.parse("2026-05-10T12:00:00Z"));
        return client;
    }

    private Service createService(Long id, String description, BigDecimal value) {
        Service service = mock(Service.class);
        when(service.getId()).thenReturn(id);
        when(service.getDescription()).thenReturn(description);
        when(service.getValue()).thenReturn(value);
        when(service.getCreatedAt()).thenReturn(Instant.parse("2026-05-10T12:00:00Z"));
        when(service.getUpdatedAt()).thenReturn(Instant.parse("2026-05-10T12:00:00Z"));
        return service;
    }
}
