package com.api.calendar;

import com.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calendar")
@Tag(name = "Google Calendar", description = "Sincronizacao com Google Calendar e consulta de eventos")
public class CalendarController {
    private final CalendarFacade facade;

    public CalendarController(final CalendarFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/sync")
    @Operation(
            summary = "Disparar sincronizacao",
            description = "Executa sync (full ou incremental) com o Google Calendar do usuario",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Sincronizacao concluida"),
                    @ApiResponse(responseCode = "403", description = "Integracao Google revogada, reautenticacao necessaria")
            }
    )
    public ResponseEntity<CalendarApiModels.SyncResponse> triggerSync(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(
                    description = "Data inicial opcional para sincronizar eventos a partir do dia informado",
                    example = "2026-04-01"
            )
            final LocalDate startDate
    ) {
        return ResponseEntity.ok(facade.triggerSync(user.userId(), startDate));
    }

    @PostMapping("/events")
    @Operation(
            summary = "Criar agendamento manual",
            description = "Cria um novo agendamento manual no dominio do calendario para o usuario autenticado",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Agendamento criado"),
                    @ApiResponse(responseCode = "400", description = "Payload invalido"),
                    @ApiResponse(responseCode = "404", description = "Cliente ou servico nao encontrado"),
                    @ApiResponse(responseCode = "422", description = "Regra de negocio violada")
            }
    )
    public ResponseEntity<CalendarApiModels.EventResponse> createManualAppointment(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @Valid @RequestBody final ManualAppointmentCreateRequest request
    ) {
        final CalendarApiModels.EventResponse created = facade.createManualAppointment(user.userId(), request.toModel());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/events")
    @Operation(
            summary = "Listar eventos sincronizados",
            description = "Retorna eventos sincronizados, com paginacao e filtros opcionais por periodo"
    )
    public ResponseEntity<Page<CalendarApiModels.EventResponse>> listEvents(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate eventStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate eventEnd,
            final Pageable pageable
    ) {
        return ResponseEntity.ok(facade.listEvents(user.userId(), eventStart, eventEnd, pageable));
    }

    @GetMapping("/status")
    @Operation(
            summary = "Status da integracao Google",
            description = "Retorna o estado atual da integracao (SYNCED, REAUTH_REQUIRED, etc.)"
    )
    public ResponseEntity<CalendarApiModels.IntegrationStatusResponse> getIntegrationStatus(
            @AuthenticationPrincipal final AuthenticatedUser user
    ) {
        return ResponseEntity.ok(facade.getIntegrationStatus(user.userId()));
    }

    @PatchMapping("/events/{eventId}/payments")
    @Operation(summary = "Registrar composicao de pagamentos do agendamento")
    public ResponseEntity<CalendarApiModels.PaymentsResponse> upsertPayments(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable final Long eventId,
            @RequestBody final CalendarApiModels.PaymentsUpsertRequest request
    ) {
        return ResponseEntity.ok(facade.upsertPayments(user.userId(), eventId, request));
    }

    @GetMapping("/events/{eventId}/payments")
    @Operation(summary = "Consultar composicao de pagamentos do agendamento")
    public ResponseEntity<CalendarApiModels.PaymentsResponse> getPayments(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable final Long eventId
    ) {
        return ResponseEntity.ok(facade.getPayments(user.userId(), eventId));
    }

    public record ManualAppointmentCreateRequest(
            @NotNull Long clientId,
            @NotNull LocalDate appointmentDate,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotEmpty java.util.List<@NotNull Long> serviceIds
    ) {
        public CalendarApiModels.ManualAppointmentCreateRequest toModel() {
            return new CalendarApiModels.ManualAppointmentCreateRequest(
                    clientId, appointmentDate, startTime, endTime, serviceIds
            );
        }
    }
}
