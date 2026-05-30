package com.api.servicecatalog;

import com.api.auth.AuthenticatedUser;
import com.api.common.PageRequestSanitizer;
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

@SuppressWarnings({"PMD.CommentDefaultAccessModifier", "PMD.LawOfDemeter", "PMD.LongVariable", "PMD.ShortVariable"})
@RestController
@RequestMapping("/api/services")
@Tag(name = "CatÃ¡logo de ServiÃ§os", description = "CRUD de serviÃ§os do usuÃ¡rio (descriÃ§Ã£o + valor)")
public class ServiceCatalogController {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id",
            "description",
            "value",
            "createdAt"
    );

    private final ServiceCatalogService serviceCatalogService;

    public ServiceCatalogController(final ServiceCatalogService serviceCatalogService) {
        this.serviceCatalogService = serviceCatalogService;
    }

    @PostMapping
    @Operation(summary = "Criar serviÃ§o", description = "Cadastra um novo serviÃ§o com descriÃ§Ã£o e valor",
            responses = {
                    @ApiResponse(responseCode = "201", description = "ServiÃ§o criado"),
                    @ApiResponse(responseCode = "400", description = "Dados invÃ¡lidos"),
                    @ApiResponse(responseCode = "422", description = "ServiÃ§o com mesma descriÃ§Ã£o jÃ¡ existe")
            })
    public ResponseEntity<ServiceResponse> createService(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @Valid @RequestBody final CreateServiceRequest request) {
        final Service service = serviceCatalogService.createService(user.userId(), request.description(), request.value());
        return ResponseEntity.status(HttpStatus.CREATED).body(ServiceResponse.from(service));
    }

    @GetMapping
    @Operation(summary = "Listar serviÃ§os",
            description = "Retorna serviÃ§os do usuÃ¡rio autenticado. Permite busca por descriÃ§Ã£o e ordenaÃ§Ã£o por description, value ou createdAt.")
    public ResponseEntity<PaginatedServiceResponse> listServices(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestParam(required = false) @Parameter(description = "Filtrar por descriÃ§Ã£o (parcial, case-insensitive)") final String description,
            @RequestParam(defaultValue = "id") @Parameter(description = "Campo de ordenaÃ§Ã£o: id, description, value, createdAt") final String sortBy,
            @RequestParam(defaultValue = "asc") @Parameter(description = "DireÃ§Ã£o: asc ou desc") final String direction,
            @RequestParam(defaultValue = "0") @Parameter(description = "PÃ¡gina (final 0-based)") final int page,
            @RequestParam(defaultValue = "25") @Parameter(description = "Itens por pÃ¡gina") final int size) {
        final Pageable pageable = PageRequestSanitizer.sanitizeZeroBased(
                page,
                size,
                sortBy,
                direction,
                ALLOWED_SORT_FIELDS,
                MAX_PAGE_SIZE
        );
        final Page<Service> servicePage = serviceCatalogService.listServices(user.userId(), description, pageable);
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
    @Operation(summary = "Buscar serviÃ§o por ID", responses = {
            @ApiResponse(responseCode = "200", description = "ServiÃ§o encontrado"),
            @ApiResponse(responseCode = "404", description = "ServiÃ§o nÃ£o encontrado")
    })
    public ResponseEntity<ServiceResponse> getService(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable final Long id) {
        final Service service = serviceCatalogService.getService(user.userId(), id);
        return ResponseEntity.ok(ServiceResponse.from(service));
    }

    @PutMapping("/{id:\\d+}")
    @Operation(summary = "Atualizar serviÃ§o", description = "Atualiza descriÃ§Ã£o e/ou valor. Dispara reprocessamento de eventos nÃ£o identificados.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "ServiÃ§o atualizado"),
                    @ApiResponse(responseCode = "404", description = "ServiÃ§o nÃ£o encontrado"),
                    @ApiResponse(responseCode = "422", description = "DescriÃ§Ã£o duplicada")
            })
    public ResponseEntity<ServiceResponse> updateService(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable final Long id,
            @Valid @RequestBody final UpdateServiceRequest request) {
        final Service service = serviceCatalogService.updateService(user.userId(), id, request.description(), request.value());
        return ResponseEntity.ok(ServiceResponse.from(service));
    }

    @DeleteMapping("/{id:\\d+}")
    @Operation(summary = "Excluir serviÃ§o", description = "Exclui o serviÃ§o. Bloqueado se houver eventos vinculados.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "ServiÃ§o excluÃ­do"),
                    @ApiResponse(responseCode = "404", description = "ServiÃ§o nÃ£o encontrado"),
                    @ApiResponse(responseCode = "422", description = "ServiÃ§o possui eventos vinculados")
            })
    public ResponseEntity<Void> deleteService(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable final Long id) {
        serviceCatalogService.deleteService(user.userId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/delete")
    @Operation(summary = "Excluir vÃ¡rios serviÃ§os",
            description = "Recebe uma lista de IDs e remove apenas serviÃ§os sem vÃ­nculo com eventos sincronizados.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Processamento concluÃ­do com resumo de exclusÃµes")
            })
    public ResponseEntity<DeleteManyServicesResponse> deleteManyServices(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestBody final List<Long> ids) {
        final ServiceCatalogService.BulkDeleteResult result = serviceCatalogService.deleteServices(user.userId(), ids);
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

    public record ServiceResponse(Long id, String description, BigDecimal value, String createdAt, String updatedAt) {
        static ServiceResponse from(final Service service) {
            return new ServiceResponse(
                    service.getId(),
                    service.getDescription(),
                    service.getValue(),
                    service.getCreatedAt().toString(),
                    service.getUpdatedAt().toString()
            );
        }
    }

    public record DeleteManyServicesResponse(int deleted, int hasLink) {}

    public record PaginatedServiceResponse(List<ServiceResponse> items, int totalItems, int itemsPerPage, int totalPages, int pageIndex) {}
}
