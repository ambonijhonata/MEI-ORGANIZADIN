package com.api.client;

import com.api.calendar.CalendarEventRepository;
import com.api.common.BusinessException;
import com.api.common.ResourceNotFoundException;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.api.user.User;
import com.api.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock private ClientRepository clientRepository;
    @Mock private UserRepository userRepository;
    @Mock private CalendarEventRepository calendarEventRepository;
    @Mock private ServiceDescriptionNormalizer normalizer;

    private ClientService clientService;

    @BeforeEach
    void setUp() {
        clientService = new ClientService(clientRepository, userRepository, calendarEventRepository, normalizer);
    }

    @Test
    void shouldCreateClientSuccessfully() {
        User user = new User("sub", "email@test.com", "Name");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(normalizer.normalize("Maria Silva")).thenReturn("maria silva");
        when(clientRepository.existsByUserIdAndNormalizedName(1L, "maria silva")).thenReturn(false);
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        var request = new ClientService.ClientRequest("Maria Silva", "12345678901", LocalDate.of(1990, 1, 1), "maria@test.com", "11999999999");
        Client result = clientService.createClient(1L, request);

        assertNotNull(result);
        assertEquals("Maria Silva", result.getName());
        assertEquals("maria silva", result.getNormalizedName());
        assertEquals("12345678901", result.getCpf());
        assertEquals("maria@test.com", result.getEmail());
    }

    @Test
    void shouldThrowWhenUserNotFoundOnCreate() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        var request = new ClientService.ClientRequest("Maria", null, null, null, null);
        assertThrows(ResourceNotFoundException.class, () -> clientService.createClient(99L, request));
    }

    @Test
    void shouldListClientsWithoutFilter() {
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        when(clientRepository.findByUserId(1L, sort)).thenReturn(List.of());

        List<Client> result = clientService.listClients(1L, null, sort);

        assertNotNull(result);
        verify(clientRepository).findByUserId(1L, sort);
        verify(clientRepository, never()).findByUserIdAndNameContainingIgnoreCase(anyLong(), anyString(), any(Sort.class));
    }

    @Test
    void shouldListClientsWithNameFilter() {
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        when(clientRepository.findByUserIdAndNameContainingIgnoreCase(1L, "Maria", sort)).thenReturn(List.of());

        List<Client> result = clientService.listClients(1L, "Maria", sort);

        assertNotNull(result);
        verify(clientRepository).findByUserIdAndNameContainingIgnoreCase(1L, "Maria", sort);
    }

    @Test
    void shouldListClientsWithBlankFilterAsNoFilter() {
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        when(clientRepository.findByUserId(1L, sort)).thenReturn(List.of());

        clientService.listClients(1L, "  ", sort);

        verify(clientRepository).findByUserId(1L, sort);
    }

    @Test
    void shouldListClientsPaginatedWithoutFilter() {
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        Page<Client> page = new PageImpl<>(List.of());
        when(clientRepository.findByUserId(eq(1L), any(PageRequest.class))).thenReturn(page);

        Page<Client> result = clientService.listClientsPaginated(1L, null, 1, 25, sort);

        assertNotNull(result);
        verify(clientRepository).findByUserId(eq(1L), any(PageRequest.class));
    }

    @Test
    void shouldListClientsPaginatedWithNameFilter() {
        Sort sort = Sort.by(Sort.Direction.ASC, "id");
        Page<Client> page = new PageImpl<>(List.of());
        when(clientRepository.findByUserIdAndNameContainingIgnoreCase(eq(1L), eq("Ana"), any(PageRequest.class))).thenReturn(page);

        Page<Client> result = clientService.listClientsPaginated(1L, "Ana", 1, 25, sort);

        assertNotNull(result);
        verify(clientRepository).findByUserIdAndNameContainingIgnoreCase(eq(1L), eq("Ana"), any(PageRequest.class));
    }

    @Test
    void shouldGetClientSuccessfully() {
        User user = new User("sub", "email@test.com", "Name");
        Client client = new Client(user, "Maria", "maria");
        when(clientRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(client));

        Client result = clientService.getClient(1L, 1L);

        assertEquals("Maria", result.getName());
    }

    @Test
    void shouldThrowWhenClientNotFound() {
        when(clientRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> clientService.getClient(1L, 99L));
    }

    @Test
    void shouldUpdateClientSuccessfully() {
        User user = new User("sub", "email@test.com", "Name");
        Client existing = new Client(user, "Old Name", "old name");
        when(clientRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));
        when(normalizer.normalize("New Name")).thenReturn("new name");
        when(clientRepository.existsByUserIdAndNormalizedNameAndIdNot(1L, "new name", 1L)).thenReturn(false);
        when(clientRepository.save(existing)).thenReturn(existing);

        var request = new ClientService.ClientRequest("New Name", "99999999999", LocalDate.of(1995, 5, 5), "new@test.com", "11888888888");
        Client result = clientService.updateClient(1L, 1L, request);

        assertEquals("New Name", result.getName());
        assertEquals("new name", result.getNormalizedName());
        assertEquals("99999999999", result.getCpf());
        assertEquals("new@test.com", result.getEmail());
        assertEquals("11888888888", result.getPhone());
    }

    @Test
    void shouldRejectDuplicateClientNameOnCreate() {
        User user = new User("sub", "email@test.com", "Name");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(normalizer.normalize("Maria Silva")).thenReturn("maria silva");
        when(clientRepository.existsByUserIdAndNormalizedName(1L, "maria silva")).thenReturn(true);

        var request = new ClientService.ClientRequest("Maria Silva", null, null, null, null);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                clientService.createClient(1L, request));

        assertEquals("Maria Silva Já cadastrado.", exception.getMessage());
        verify(clientRepository, never()).save(any());
    }

    @Test
    void shouldRejectDuplicateClientNameOnUpdateWhenAnotherRecordExists() {
        User user = new User("sub", "email@test.com", "Name");
        Client existing = new Client(user, "Old Name", "old name");
        when(clientRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));
        when(normalizer.normalize("Maria Silva")).thenReturn("maria silva");
        when(clientRepository.existsByUserIdAndNormalizedNameAndIdNot(1L, "maria silva", 1L)).thenReturn(true);

        var request = new ClientService.ClientRequest("Maria Silva", null, null, null, null);

        BusinessException exception = assertThrows(BusinessException.class, () ->
                clientService.updateClient(1L, 1L, request));

        assertEquals("Maria Silva Já cadastrado.", exception.getMessage());
        verify(clientRepository, never()).save(any());
    }

    @Test
    void shouldAllowUpdateWhenClientKeepsSameNormalizedName() {
        User user = new User("sub", "email@test.com", "Name");
        Client existing = new Client(user, "Maria Silva", "maria silva");
        when(clientRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(existing));
        when(normalizer.normalize("Maria Silva")).thenReturn("maria silva");
        when(clientRepository.existsByUserIdAndNormalizedNameAndIdNot(1L, "maria silva", 1L)).thenReturn(false);
        when(clientRepository.save(existing)).thenReturn(existing);

        var request = new ClientService.ClientRequest("Maria Silva", null, null, null, null);
        Client result = clientService.updateClient(1L, 1L, request);

        assertEquals("Maria Silva", result.getName());
        verify(clientRepository).save(existing);
    }

    @Test
    void shouldThrowWhenClientNotFoundOnUpdate() {
        when(clientRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());
        var request = new ClientService.ClientRequest("Name", null, null, null, null);
        assertThrows(ResourceNotFoundException.class, () -> clientService.updateClient(1L, 99L, request));
    }

    @Test
    void shouldDeleteClientSuccessfully() {
        User user = new User("sub", "email@test.com", "Name");
        Client client = new Client(user, "Maria", "maria");
        when(clientRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(client));
        when(calendarEventRepository.existsByClientId(1L)).thenReturn(false);

        clientService.deleteClient(1L, 1L);

        verify(clientRepository).delete(client);
    }

    @Test
    void shouldBlockDeleteWhenClientHasLinkedEvents() {
        User user = new User("sub", "email@test.com", "Name");
        Client client = new Client(user, "Maria", "maria");
        when(clientRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(client));
        when(calendarEventRepository.existsByClientId(1L)).thenReturn(true);

        assertThrows(BusinessException.class, () -> clientService.deleteClient(1L, 1L));
        verify(clientRepository, never()).delete(any());
    }

    @Test
    void shouldThrowWhenClientNotFoundOnDelete() {
        when(clientRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> clientService.deleteClient(1L, 99L));
    }

    @Test
    void shouldFindOrCreateByNameWhenExists() {
        User user = new User("sub", "email@test.com", "Name");
        Client existing = new Client(user, "Maria", "maria");
        when(normalizer.normalize("Maria")).thenReturn("maria");
        when(clientRepository.findByUserIdAndNormalizedName(1L, "maria")).thenReturn(Optional.of(existing));

        Client result = clientService.findOrCreateByName(1L, user, "Maria");

        assertEquals("Maria", result.getName());
        verify(clientRepository, never()).save(any());
    }

    @Test
    void shouldFindOrCreateByNameWhenNotExists() {
        User user = new User("sub", "email@test.com", "Name");
        when(normalizer.normalize(" Maria ")).thenReturn("maria");
        when(clientRepository.findByUserIdAndNormalizedName(1L, "maria")).thenReturn(Optional.empty());
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> inv.getArgument(0));

        Client result = clientService.findOrCreateByName(1L, user, " Maria ");

        assertEquals("Maria", result.getName());
        verify(clientRepository).save(any(Client.class));
    }

    @Test
    void shouldFindByNormalizedName() {
        User user = new User("sub", "email@test.com", "Name");
        Client client = new Client(user, "Maria", "maria");
        when(clientRepository.findByUserIdAndNormalizedName(1L, "maria")).thenReturn(Optional.of(client));

        Optional<Client> result = clientService.findByNormalizedName(1L, "maria");

        assertTrue(result.isPresent());
    }

    @Test
    void shouldReturnEmptyWhenNormalizedNameNotFound() {
        when(clientRepository.findByUserIdAndNormalizedName(1L, "unknown")).thenReturn(Optional.empty());

        Optional<Client> result = clientService.findByNormalizedName(1L, "unknown");

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldBulkDeleteClientsSuccessfully() {
        User user = new User("sub", "email@test.com", "Name");
        Client client1 = new Client(user, "Maria", "maria");
        Client client2 = new Client(user, "Ana", "ana");
        Client client3 = new Client(user, "João", "joao");

        when(clientRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(client1));
        when(clientRepository.findByIdAndUserId(2L, 1L)).thenReturn(Optional.of(client2));
        when(clientRepository.findByIdAndUserId(3L, 1L)).thenReturn(Optional.of(client3));
        when(calendarEventRepository.existsByClientId(1L)).thenReturn(false);
        when(calendarEventRepository.existsByClientId(2L)).thenReturn(true);
        when(calendarEventRepository.existsByClientId(3L)).thenReturn(false);

        var result = clientService.bulkDeleteClients(1L, List.of(1L, 2L, 3L));

        assertEquals(2, result.deleted());
        assertEquals(1, result.hasLink());
        verify(clientRepository).delete(client1);
        verify(clientRepository, never()).delete(client2);
        verify(clientRepository).delete(client3);
    }

    @Test
    void shouldBulkDeleteSkipNonExistentClients() {
        when(clientRepository.findByIdAndUserId(99L, 1L)).thenReturn(Optional.empty());

        var result = clientService.bulkDeleteClients(1L, List.of(99L));

        assertEquals(0, result.deleted());
        assertEquals(0, result.hasLink());
    }

    @Test
    void shouldBulkDeleteWithEmptyList() {
        var result = clientService.bulkDeleteClients(1L, List.of());

        assertEquals(0, result.deleted());
        assertEquals(0, result.hasLink());
    }

    @Test
    void shouldBulkDeleteAllWithLinks() {
        User user = new User("sub", "email@test.com", "Name");
        Client client1 = new Client(user, "Maria", "maria");
        Client client2 = new Client(user, "Ana", "ana");

        when(clientRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(client1));
        when(clientRepository.findByIdAndUserId(2L, 1L)).thenReturn(Optional.of(client2));
        when(calendarEventRepository.existsByClientId(1L)).thenReturn(true);
        when(calendarEventRepository.existsByClientId(2L)).thenReturn(true);

        var result = clientService.bulkDeleteClients(1L, List.of(1L, 2L));

        assertEquals(0, result.deleted());
        assertEquals(2, result.hasLink());
    }
}
