package com.api.client;

import com.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.api.calendar.CalendarEventRepository;
import com.api.servicecatalog.ServiceDescriptionNormalizer;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/clients")
@Tag(name = "Clientes", description = "CRUD de clientes do usuário")
public class ClientController {

    private final ClientService clientService;
    private final CalendarEventRepository calendarEventRepository;
    private final ServiceDescriptionNormalizer normalizer;

    public ClientController(ClientService clientService,
                             CalendarEventRepository calendarEventRepository,
                             ServiceDescriptionNormalizer normalizer) {
        this.clientService = clientService;
        this.calendarEventRepository = calendarEventRepository;
        this.normalizer = normalizer;
    }

    @PostMapping
    @Operation(summary = "Criar cliente", responses = {
            @ApiResponse(responseCode = "201", description = "Cliente criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<ClientResponse> create(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateClientRequest request) {
        var clientRequest = new ClientService.ClientRequest(
                request.name(), request.cpf(), request.dateOfBirth(), request.email(), request.phone());
        Client client = clientService.createClient(user.userId(), clientRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClientResponse.from(client));
    }

    @GetMapping
    @Operation(summary = "Listar clientes",
            description = "Retorna clientes do usuário autenticado com paginação. Permite busca por nome e ordenação por name, cpf, dateOfBirth, email, phone ou createdAt.")
    public ResponseEntity<PaginatedResponse<ClientResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @Parameter(description = "Filtrar por nome (parcial, case-insensitive)") String name,
            @RequestParam(defaultValue = "id") @Parameter(description = "Campo de ordenação: id, name, cpf, dateOfBirth, email, phone, createdAt") String sortBy,
            @RequestParam(defaultValue = "asc") @Parameter(description = "Direção: asc ou desc") String direction,
            @RequestParam(defaultValue = "1") @Parameter(description = "Página (começa em 1)") int pageIndex,
            @RequestParam(defaultValue = "25") @Parameter(description = "Itens por página") int itemsPerPage) {
        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        var page = clientService.listClientsPaginated(user.userId(), name, pageIndex, itemsPerPage, sort);
        List<ClientResponse> items = page.getContent().stream().map(ClientResponse::from).toList();
        int totalPages = (int) Math.ceil((double) page.getTotalElements() / itemsPerPage);
        return ResponseEntity.ok(new PaginatedResponse<>(
                items,
                page.getTotalElements(),
                totalPages,
                itemsPerPage,
                pageIndex
        ));
    }

    public record PaginatedResponse<T>(
            List<T> items,
            long totalItems,
            int totalPages,
            int itemsPerPage,
            int pageIndex
    ) {}

    @GetMapping("/client-has-link-with-appointment")
    @Operation(summary = "Verificar vínculo do cliente com agendamento",
            description = "Retorna true se o cliente com o nome informado está vinculado a algum agendamento",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Resultado da verificação")
            })
    public ResponseEntity<Boolean> hasLinkWithAppointment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam @Parameter(description = "Nome do cliente") String name) {
        String normalized = normalizer.normalize(name);
        return clientService.findByNormalizedName(user.userId(), normalized)
                .map(client -> ResponseEntity.ok(calendarEventRepository.existsByClientId(client.getId())))
                .orElse(ResponseEntity.ok(false));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cliente por ID", responses = {
            @ApiResponse(responseCode = "200", description = "Cliente encontrado"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado")
    })
    public ResponseEntity<ClientResponse> get(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        return ResponseEntity.ok(ClientResponse.from(clientService.getClient(user.userId(), id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar cliente", responses = {
            @ApiResponse(responseCode = "200", description = "Cliente atualizado"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado")
    })
    public ResponseEntity<ClientResponse> update(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id,
            @Valid @RequestBody CreateClientRequest request) {
        var clientRequest = new ClientService.ClientRequest(
                request.name(), request.cpf(), request.dateOfBirth(), request.email(), request.phone());
        Client client = clientService.updateClient(user.userId(), id, clientRequest);
        return ResponseEntity.ok(ClientResponse.from(client));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir cliente", responses = {
            @ApiResponse(responseCode = "204", description = "Cliente excluído"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado"),
            @ApiResponse(responseCode = "422", description = "Cliente possui agendamentos vinculados")
    })
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AuthenticatedUser user, @PathVariable Long id) {
        clientService.deleteClient(user.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/delete")
    @Operation(summary = "Excluir clientes em lote",
            description = "Exclui múltiplos clientes por ID. Clientes com agendamentos vinculados não são excluídos.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Resultado da exclusão em lote")
            })
    public ResponseEntity<BulkDeleteResponse> bulkDelete(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody List<Long> ids) {
        var result = clientService.bulkDeleteClients(user.userId(), ids);
        return ResponseEntity.ok(new BulkDeleteResponse(result.deleted(), result.hasLink()));
    }

    public record BulkDeleteResponse(int deleted, int hasLink) {}

    public record CreateClientRequest(
            @NotBlank String name,
            String cpf,
            LocalDate dateOfBirth,
            String email,
            String phone
    ) {}

    public record ClientResponse(Long id, String name, String cpf, LocalDate dateOfBirth,
                                   String email, String phone, String createdAt) {
        static ClientResponse from(Client c) {
            return new ClientResponse(c.getId(), c.getName(), c.getCpf(), c.getDateOfBirth(),
                    c.getEmail(), c.getPhone(), c.getCreatedAt().toString());
        }
    }
}
