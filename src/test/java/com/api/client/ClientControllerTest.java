package com.api.client;

import com.api.auth.AuthenticatedUser;
import com.api.calendar.CalendarEventRepository;
import com.api.common.InvalidRequestParameterException;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientControllerTest {

    @Mock
    private ClientService clientService;

    @Mock
    private CalendarEventRepository calendarEventRepository;

    @Mock
    private ServiceDescriptionNormalizer normalizer;

    private ClientController controller;
    private AuthenticatedUser user;

    @BeforeEach
    void setUp() {
        controller = new ClientController(clientService, calendarEventRepository, normalizer);
        user = new AuthenticatedUser(1L, "google-sub", "test@example.com", "Test User");
    }

    @Test
    void shouldListClientsWithDefaultPagination() {
        PageRequest pageRequest = PageRequest.of(0, 25, Sort.by(Sort.Direction.ASC, "id"));
        List<Client> clients = List.of(createClient(1L, "Maria"), createClient(2L, "Ana"));
        Page<Client> page = new PageImpl<>(clients, pageRequest, 2);
        when(clientService.listClientsPaginated(1L, null, 1, 25, pageRequest.getSort())).thenReturn(page);

        ResponseEntity<ClientController.PaginatedResponse<ClientController.ClientResponse>> response =
                controller.list(user, null, "id", "asc", 1, 25);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().items()).hasSize(2);
        assertThat(response.getBody().totalItems()).isEqualTo(2);
        assertThat(response.getBody().itemsPerPage()).isEqualTo(25);
        assertThat(response.getBody().pageIndex()).isEqualTo(1);
    }

    @Test
    void shouldRejectUnsupportedSortField() {
        assertThatThrownBy(() -> controller.list(user, null, "name;drop table clients", "asc", 1, 25))
                .isInstanceOf(InvalidRequestParameterException.class)
                .hasMessageContaining("sortBy");
    }

    @Test
    void shouldRejectInvalidDirection() {
        assertThatThrownBy(() -> controller.list(user, null, "name", "desc;drop", 1, 25))
                .isInstanceOf(InvalidRequestParameterException.class)
                .hasMessageContaining("direction");
    }

    @Test
    void shouldRejectInvalidPageIndex() {
        assertThatThrownBy(() -> controller.list(user, null, "name", "asc", 0, 25))
                .isInstanceOf(InvalidRequestParameterException.class)
                .hasMessageContaining("pageIndex");
    }

    @Test
    void shouldRejectInvalidItemsPerPage() {
        assertThatThrownBy(() -> controller.list(user, null, "name", "asc", 1, 0))
                .isInstanceOf(InvalidRequestParameterException.class)
                .hasMessageContaining("itemsPerPage");
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
}
