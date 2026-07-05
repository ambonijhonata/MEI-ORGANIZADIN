package com.api.client;

import com.api.auth.AuthenticatedUser;
import com.api.calendar.CalendarEventRepository;
import com.api.common.PageRequestSanitizer;
import com.api.servicecatalog.ServiceDescriptionNormalizer;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/clients")
@Tag(name = "Clientes", description = "CRUD de clientes do usuário")
public class ClientController {
    private static final String OK_CODE = "200";
    private static final String NOT_FOUND = "404";
    private static final int MAX_PAGE = 100;
    private static final Set<String> SORTS = Set.of(
            "id",
            "name",
            "cpf",
            "dateOfBirth",
            "email",
            "phone",
            "createdAt"
    );

    private final ClientService clientService;
    private final CalendarEventRepository eventRepo;
    private final ServiceDescriptionNormalizer normalizer;

    public ClientController(
            final ClientService clientService,
            final CalendarEventRepository eventRepo,
            final ServiceDescriptionNormalizer normalizer) {
        this.clientService = clientService;
        this.eventRepo = eventRepo;
        this.normalizer = normalizer;
    }

    @PostMapping
    @Operation(summary = "Criar cliente", responses = {
            @ApiResponse(responseCode = "201", description = "Cliente criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos")
    })
    public ResponseEntity<ClientResponse> create(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @Valid @RequestBody final CreateClientRequest request) {
        final Client client = clientService.createClient(user.userId(), toClientRequest(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(ClientResponse.from(client));
    }

    @GetMapping
    @Operation(
            summary = "Listar clientes",
            description = "Retorna clientes do usuário autenticado com paginação. "
                    + "Permite busca por nome e ordenação por name, cpf, dateOfBirth, email, phone ou createdAt."
    )
    public ResponseEntity<PaginatedResponse<ClientResponse>> list(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestParam(required = false)
            @Parameter(description = "Filtrar por nome (prefixo, case-insensitive)")
            final String name,
            @RequestParam(defaultValue = "id")
            @Parameter(description = "Campo de ordenação: id, name, cpf, dateOfBirth, email, phone, createdAt")
            final String sortBy,
            @RequestParam(defaultValue = "asc")
            @Parameter(description = "Direção: asc ou desc")
            final String direction,
            @RequestParam(defaultValue = "1")
            @Parameter(description = "Página (começa em 1)")
            final int pageIndex,
            @RequestParam(defaultValue = "25")
            @Parameter(description = "Itens por página")
            final int itemsPerPage) {
        final PageRequest pageRequest = PageRequestSanitizer.sanitizeOneBased(
                pageIndex,
                itemsPerPage,
                sortBy,
                direction,
                SORTS,
                MAX_PAGE
        );
        final int page = pageRequest.getPageNumber() + 1;
        final int size = pageRequest.getPageSize();
        final org.springframework.data.domain.Page<Client> clientPage = clientService.listClientsPaginated(
                user.userId(),
                name,
                page,
                size,
                pageRequest.getSort()
        );
        return ResponseEntity.ok(PaginatedResponse.from(clientPage, size, page));
    }

    @GetMapping("/client-has-link-with-appointment")
    @Operation(
            summary = "Verificar vínculo do cliente com agendamento",
            description = "Retorna true se o cliente com o nome informado está vinculado a algum agendamento",
            responses = {
                    @ApiResponse(responseCode = OK_CODE, description = "Resultado da verificação")
            }
    )
    public ResponseEntity<Boolean> getLinkWithAppointmentStatus(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestParam @Parameter(description = "Nome do cliente") final String name) {
        final String normalizedName = normalizer.normalize(name);
        return clientService.findByNormalizedName(user.userId(), normalizedName)
                .map(client -> ResponseEntity.ok(eventRepo.existsByClientId(client.getId())))
                .orElse(ResponseEntity.ok(false));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cliente por ID", responses = {
            @ApiResponse(responseCode = OK_CODE, description = "Cliente encontrado"),
            @ApiResponse(responseCode = NOT_FOUND, description = "Cliente não encontrado")
    })
    public ResponseEntity<ClientResponse> get(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable("id") final Long clientId) {
        return ResponseEntity.ok(ClientResponse.from(clientService.getClient(user.userId(), clientId)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar cliente", responses = {
            @ApiResponse(responseCode = OK_CODE, description = "Cliente atualizado"),
            @ApiResponse(responseCode = NOT_FOUND, description = "Cliente não encontrado")
    })
    public ResponseEntity<ClientResponse> update(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable("id") final Long clientId,
            @Valid @RequestBody final CreateClientRequest request) {
        final Client client = clientService.updateClient(user.userId(), clientId, toClientRequest(request));
        return ResponseEntity.ok(ClientResponse.from(client));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir cliente", responses = {
            @ApiResponse(responseCode = "204", description = "Cliente excluído"),
            @ApiResponse(responseCode = NOT_FOUND, description = "Cliente não encontrado"),
            @ApiResponse(responseCode = "422", description = "Cliente possui agendamentos vinculados")
    })
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable("id") final Long clientId) {
        clientService.deleteClient(user.userId(), clientId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/delete")
    @Operation(
            summary = "Excluir clientes em lote",
            description = "Exclui múltiplos clientes por ID. Clientes com agendamentos vinculados não são excluídos.",
            responses = {
                    @ApiResponse(responseCode = OK_CODE, description = "Resultado da exclusão em lote")
            }
    )
    public ResponseEntity<BulkDeleteResponse> bulkDelete(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestBody final List<Long> ids) {
        final ClientService.BulkDeleteResult result = clientService.bulkDeleteClients(user.userId(), ids);
        return ResponseEntity.ok(new BulkDeleteResponse(result.deleted(), result.hasLink()));
    }

    private ClientService.ClientRequest toClientRequest(final CreateClientRequest request) {
        return new ClientService.ClientRequest(
                request.name(),
                request.cpf(),
                request.dateOfBirth(),
                request.email(),
                request.phone()
        );
    }

    public record PaginatedResponse<T>(
            List<T> items,
            long totalItems,
            int totalPages,
            int itemsPerPage,
            int pageIndex
    ) {
        public static PaginatedResponse<ClientResponse> from(
                final org.springframework.data.domain.Page<Client> clientPage,
                final int itemsPerPage,
                final int pageIndex) {
            final List<ClientResponse> items = clientPage.getContent().stream()
                    .map(ClientResponse::from)
                    .toList();
            final int totalPages = (int) Math.ceil((double) clientPage.getTotalElements() / itemsPerPage);
            return new PaginatedResponse<>(
                    items,
                    clientPage.getTotalElements(),
                    totalPages,
                    itemsPerPage,
                    pageIndex
            );
        }
    }

    public record BulkDeleteResponse(
            int deleted,
            @JsonProperty("hasLink") int linkedItems
    ) {}

    public record CreateClientRequest(
            @NotBlank String name,
            String cpf,
            LocalDate dateOfBirth,
            String email,
            String phone
    ) {}

    public record ClientResponse(
            @JsonProperty("id") Long clientId,
            String name,
            String cpf,
            LocalDate dateOfBirth,
            String email,
            String phone,
            String createdAt
    ) {
        public static ClientResponse from(final Client client) {
            return new ClientResponse(
                    client.getId(),
                    client.getName(),
                    client.getCpf(),
                    client.getDateOfBirth(),
                    client.getEmail(),
                    client.getPhone(),
                    String.valueOf(client.getCreatedAt())
            );
        }
    }
}
