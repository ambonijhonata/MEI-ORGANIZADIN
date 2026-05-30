package com.api.client;

import com.api.auth.AuthenticatedUser;
import com.api.common.PageRequestSanitizer;
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
import java.util.Set;

@SuppressWarnings({"PMD.AvoidDuplicateLiterals", "PMD.CommentDefaultAccessModifier", "PMD.LawOfDemeter", "PMD.LinguisticNaming", "PMD.LongVariable", "PMD.ShortVariable", "PMD.UseExplicitTypes"})
@RestController
@RequestMapping("/api/clients")
@Tag(name = "Clientes", description = "CRUD de clientes do usuário")
public class ClientController {
    private static final int MAX_ITEMS_PER_PAGE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id",
            "name",
            "cpf",
            "dateOfBirth",
            "email",
            "phone",
            "createdAt"
    );

    private final ClientService clientService;
    private final CalendarEventRepository calendarEventRepository;
    private final ServiceDescriptionNormalizer normalizer;

    public ClientController(final ClientService clientService,
                             final CalendarEventRepository calendarEventRepository,
                             final ServiceDescriptionNormalizer normalizer) {
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
            @AuthenticationPrincipal final AuthenticatedUser user,
            @Valid @RequestBody final CreateClientRequest request) {
        final var clientRequest = new ClientService.ClientRequest(
                request.name(), request.cpf(), request.dateOfBirth(), request.email(), request.phone());
        final Client client = clientService.createClient(user.userId(), clientRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClientResponse.from(client));
    }

    @GetMapping
    @Operation(summary = "Listar clientes",
            description = "Retorna clientes do usuário autenticado com paginação. Permite busca por nome e ordenação por name, cpf, dateOfBirth, email, phone ou createdAt.")
    public ResponseEntity<PaginatedResponse<ClientResponse>> list(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestParam(required = false) @Parameter(description = "Filtrar por nome (prefixo, case-insensitive)") final String name,
            @RequestParam(defaultValue = "id") @Parameter(description = "Campo de ordenação: id, name, cpf, dateOfBirth, email, phone, createdAt") final String sortBy,
            @RequestParam(defaultValue = "asc") @Parameter(description = "Direção: asc ou desc") final String direction,
            @RequestParam(defaultValue = "1") @Parameter(description = "Página (final começa em 1)") final int pageIndex,
            @RequestParam(defaultValue = "25") @Parameter(description = "Itens por página") final int itemsPerPage) {
        final var sanitizedPageRequest = PageRequestSanitizer.sanitizeOneBased(
                pageIndex,
                itemsPerPage,
                sortBy,
                direction,
                ALLOWED_SORT_FIELDS,
                MAX_ITEMS_PER_PAGE
        );
        final Sort sort = sanitizedPageRequest.getSort();
        final int sanitizedPageIndex = sanitizedPageRequest.getPageNumber() + 1;
        final int sanitizedItemsPerPage = sanitizedPageRequest.getPageSize();
        final var page = clientService.listClientsPaginated(
                user.userId(),
                name,
                sanitizedPageIndex,
                sanitizedItemsPerPage,
                sort
        );
        final List<ClientResponse> items = page.getContent().stream().map(ClientResponse::from).toList();
        final int totalPages = (int) Math.ceil((double) page.getTotalElements() / sanitizedItemsPerPage);
        return ResponseEntity.ok(new PaginatedResponse<>(
                items,
                page.getTotalElements(),
                totalPages,
                sanitizedItemsPerPage,
                sanitizedPageIndex
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
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestParam @Parameter(description = "Nome do cliente") final String name) {
        final String normalized = normalizer.normalize(name);
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
            @AuthenticationPrincipal final AuthenticatedUser user, @PathVariable final Long id) {
        return ResponseEntity.ok(ClientResponse.from(clientService.getClient(user.userId(), id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar cliente", responses = {
            @ApiResponse(responseCode = "200", description = "Cliente atualizado"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado")
    })
    public ResponseEntity<ClientResponse> update(
            @AuthenticationPrincipal final AuthenticatedUser user, @PathVariable final Long id,
            @Valid @RequestBody final CreateClientRequest request) {
        final var clientRequest = new ClientService.ClientRequest(
                request.name(), request.cpf(), request.dateOfBirth(), request.email(), request.phone());
        final Client client = clientService.updateClient(user.userId(), id, clientRequest);
        return ResponseEntity.ok(ClientResponse.from(client));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir cliente", responses = {
            @ApiResponse(responseCode = "204", description = "Cliente excluído"),
            @ApiResponse(responseCode = "404", description = "Cliente não encontrado"),
            @ApiResponse(responseCode = "422", description = "Cliente possui agendamentos vinculados")
    })
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal final AuthenticatedUser user, @PathVariable final Long id) {
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
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestBody final List<Long> ids) {
        final var result = clientService.bulkDeleteClients(user.userId(), ids);
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
        static ClientResponse from(final Client c) {
            return new ClientResponse(c.getId(), c.getName(), c.getCpf(), c.getDateOfBirth(),
                    c.getEmail(), c.getPhone(), c.getCreatedAt().toString());
        }
    }
}
