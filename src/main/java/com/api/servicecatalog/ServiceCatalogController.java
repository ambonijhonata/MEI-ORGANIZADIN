package com.api.servicecatalog;

import com.api.auth.AuthenticatedUser;
import com.api.common.PageRequestSanitizer;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/services")
@Tag(name = "Catálogo de Serviços", description = "CRUD de serviços do usuário (descrição + valor)")
public class ServiceCatalogController {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS = Set.of(
            "id",
            "description",
            "value",
            "createdAt"
    );

    private final ServiceCatalogService catalogService;

    public ServiceCatalogController(final ServiceCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @PostMapping
    @Operation(summary = "Criar serviço", description = "Cadastra um novo serviço com descrição e valor",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Serviço criado"),
                    @ApiResponse(responseCode = "400", description = "Dados inválidos"),
                    @ApiResponse(responseCode = "422", description = "Serviço com mesma descrição já existe")
            })
    public ResponseEntity<ServiceResponse> createService(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @Valid @RequestBody final CreateServiceRequest request) {
        final Service service = catalogService.createService(user.userId(), request.description(), request.value());
        return ResponseEntity.status(HttpStatus.CREATED).body(ServiceResponse.from(service));
    }

    @GetMapping
    @Operation(summary = "Listar serviços",
            description = "Retorna serviços do usuário autenticado. Permite busca por descrição e ordenação por description, value ou createdAt.")
    public ResponseEntity<PaginatedServiceResponse> listServices(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestParam(required = false) @Parameter(description = "Filtrar por descrição (parcial, case-insensitive)") final String description,
            @RequestParam(defaultValue = "id") @Parameter(description = "Campo de ordenação: id, description, value, createdAt") final String sortBy,
            @RequestParam(defaultValue = "asc") @Parameter(description = "Direção: asc ou desc") final String direction,
            @RequestParam(defaultValue = "0") @Parameter(description = "Página (final 0-based)") final int page,
            @RequestParam(defaultValue = "25") @Parameter(description = "Itens por página") final int size) {
        final Pageable pageable = PageRequestSanitizer.sanitizeZeroBased(
                page,
                size,
                sortBy,
                direction,
                SORT_FIELDS,
                MAX_PAGE_SIZE
        );
        final Page<Service> servicePage = catalogService.listServices(user.userId(), description, pageable);
        final List<ServiceResponse> items = servicePage.getContent().stream()
                .map(ServiceResponse::from)
                .toList();
        final PaginatedServiceResponse response = new PaginatedServiceResponse(
                items,
                (int) servicePage.getTotalElements(),
                servicePage.getSize(),
                servicePage.getTotalPages(),
                servicePage.getNumber()
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id:\\d+}")
    @Operation(summary = "Buscar serviço por ID", responses = {
            @ApiResponse(responseCode = "200", description = "Serviço encontrado"),
            @ApiResponse(responseCode = "404", description = "Serviço não encontrado")
    })
    public ResponseEntity<ServiceResponse> getService(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable("id") final Long serviceId) {
        final Service service = catalogService.getService(user.userId(), serviceId);
        return ResponseEntity.ok(ServiceResponse.from(service));
    }

    @PutMapping("/{id:\\d+}")
    @Operation(summary = "Atualizar serviço", description = "Atualiza descrição e/ou valor. Dispara reprocessamento de eventos não identificados.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Serviço atualizado"),
                    @ApiResponse(responseCode = "404", description = "Serviço não encontrado"),
                    @ApiResponse(responseCode = "422", description = "Descrição duplicada")
    })
    public ResponseEntity<ServiceResponse> updateService(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable("id") final Long serviceId,
            @Valid @RequestBody final UpdateServiceRequest request) {
        final Service service = catalogService.updateService(user.userId(), serviceId, request.description(), request.value());
        return ResponseEntity.ok(ServiceResponse.from(service));
    }

    @DeleteMapping("/{id:\\d+}")
    @Operation(summary = "Excluir serviço", description = "Exclui o serviço. Bloqueado se houver eventos vinculados.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Serviço excluído"),
                    @ApiResponse(responseCode = "404", description = "Serviço não encontrado"),
                    @ApiResponse(responseCode = "422", description = "Serviço possui eventos vinculados")
    })
    public ResponseEntity<Void> deleteService(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable("id") final Long serviceId) {
        catalogService.deleteService(user.userId(), serviceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/delete")
    @Operation(summary = "Excluir vários serviços",
            description = "Recebe uma lista de IDs e remove apenas serviços sem vínculo com eventos sincronizados.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Processamento concluído com resumo de exclusões")
            })
    public ResponseEntity<DeleteManyServicesResponse> deleteManyServices(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestBody final List<Long> ids) {
        final ServiceCatalogService.BulkDeleteResult result = catalogService.deleteServices(user.userId(), ids);
        return ResponseEntity.ok(new DeleteManyServicesResponse(result.deleted(), result.hasLink()));
    }

    public record CreateServiceRequest(
            @NotBlank String description,
            @NotNull @Positive BigDecimal value
    ) {}

    public record UpdateServiceRequest(
            @NotBlank String description,
            @NotNull @Positive BigDecimal value
    ) {}

    public record ServiceResponse(
            @JsonProperty("id") Long serviceId,
            String description,
            BigDecimal value,
            String createdAt,
            String updatedAt
    ) {
        private static ServiceResponse from(final Service service) {
            return new ServiceResponse(
                    service.getId(),
                    service.getDescription(),
                    service.getValue(),
                    service.createdAtText(),
                    service.updatedAtText()
            );
        }
    }

    public record DeleteManyServicesResponse(int deleted, int hasLink) {}

    public record PaginatedServiceResponse(List<ServiceResponse> items, int totalItems, int itemsPerPage, int totalPages, int pageIndex) {}
}
