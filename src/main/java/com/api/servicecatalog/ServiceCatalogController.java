package com.api.servicecatalog;

import com.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/services")
@Tag(name = "Catálogo de Serviços", description = "CRUD de serviços do usuário (descrição + valor)")
public class ServiceCatalogController {

    private final ServiceCatalogService serviceCatalogService;

    public ServiceCatalogController(ServiceCatalogService serviceCatalogService) {
        this.serviceCatalogService = serviceCatalogService;
    }

    @PostMapping
    @Operation(summary = "Criar serviço", description = "Cadastra um novo serviço com descrição e valor",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Serviço criado"),
                    @ApiResponse(responseCode = "400", description = "Dados inválidos"),
                    @ApiResponse(responseCode = "422", description = "Serviço com mesma descrição já existe")
            })
    public ResponseEntity<ServiceResponse> createService(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateServiceRequest request) {
        Service service = serviceCatalogService.createService(user.userId(), request.description(), request.value());
        return ResponseEntity.status(HttpStatus.CREATED).body(ServiceResponse.from(service));
    }

    @GetMapping
    @Operation(summary = "Listar serviços",
            description = "Retorna serviços do usuário autenticado. Permite busca por descrição e ordenação por description, value ou createdAt.")
    public ResponseEntity<List<ServiceResponse>> listServices(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @Parameter(description = "Filtrar por descrição (parcial, case-insensitive)") String description,
            @RequestParam(defaultValue = "id") @Parameter(description = "Campo de ordenação: id, description, value, createdAt") String sortBy,
            @RequestParam(defaultValue = "asc") @Parameter(description = "Direção: asc ou desc") String direction) {
        Sort sort = Sort.by(Sort.Direction.fromString(direction), sortBy);
        List<ServiceResponse> services = serviceCatalogService.listServices(user.userId(), description, sort).stream()
                .map(ServiceResponse::from)
                .toList();
        return ResponseEntity.ok(services);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar serviço por ID", responses = {
            @ApiResponse(responseCode = "200", description = "Serviço encontrado"),
            @ApiResponse(responseCode = "404", description = "Serviço não encontrado")
    })
    public ResponseEntity<ServiceResponse> getService(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        Service service = serviceCatalogService.getService(user.userId(), id);
        return ResponseEntity.ok(ServiceResponse.from(service));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar serviço", description = "Atualiza descrição e/ou valor. Dispara reprocessamento de eventos não identificados.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Serviço atualizado"),
                    @ApiResponse(responseCode = "404", description = "Serviço não encontrado"),
                    @ApiResponse(responseCode = "422", description = "Descrição duplicada")
            })
    public ResponseEntity<ServiceResponse> updateService(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id,
            @Valid @RequestBody UpdateServiceRequest request) {
        Service service = serviceCatalogService.updateService(user.userId(), id, request.description(), request.value());
        return ResponseEntity.ok(ServiceResponse.from(service));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir serviço", description = "Exclui o serviço. Bloqueado se houver eventos vinculados.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Serviço excluído"),
                    @ApiResponse(responseCode = "404", description = "Serviço não encontrado"),
                    @ApiResponse(responseCode = "422", description = "Serviço possui eventos vinculados")
            })
    public ResponseEntity<Void> deleteService(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long id) {
        serviceCatalogService.deleteService(user.userId(), id);
        return ResponseEntity.noContent().build();
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
        static ServiceResponse from(Service service) {
            return new ServiceResponse(
                    service.getId(),
                    service.getDescription(),
                    service.getValue(),
                    service.getCreatedAt().toString(),
                    service.getUpdatedAt().toString()
            );
        }
    }
}
