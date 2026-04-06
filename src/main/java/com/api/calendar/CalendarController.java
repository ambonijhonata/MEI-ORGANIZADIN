package com.api.calendar;

import com.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/api/calendar")
@Tag(name = "Google Calendar", description = "Sincronização com Google Calendar e consulta de eventos")
public class CalendarController {

    private final CalendarSyncService calendarSyncService;
    private final CalendarEventRepository calendarEventRepository;
    private final SyncStateRepository syncStateRepository;

    public CalendarController(CalendarSyncService calendarSyncService,
                               CalendarEventRepository calendarEventRepository,
                               SyncStateRepository syncStateRepository) {
        this.calendarSyncService = calendarSyncService;
        this.calendarEventRepository = calendarEventRepository;
        this.syncStateRepository = syncStateRepository;
    }

    @PostMapping("/sync")
    @Operation(summary = "Disparar sincronização", description = "Executa sync (full ou incremental) com o Google Calendar do usuário",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Sincronização concluída"),
                    @ApiResponse(responseCode = "403", description = "Integração Google revogada, reautenticação necessária")
            })
    public ResponseEntity<SyncResponse> triggerSync(@AuthenticationPrincipal AuthenticatedUser user) {
        CalendarSyncService.SyncResult result = calendarSyncService.synchronize(user.userId());
        return ResponseEntity.ok(new SyncResponse(result.created(), result.updated(), result.deleted()));
    }

    @GetMapping("/events")
    @Operation(summary = "Listar eventos sincronizados", description = "Retorna eventos do Google Calendar sincronizados, com paginação. Filtros opcionais por período (eventStart e eventEnd).")
    public ResponseEntity<Page<EventResponse>> listEvents(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate eventEnd,
            Pageable pageable) {
        Page<CalendarEvent> page;
        if (eventStart != null && eventEnd != null) {
            Instant start = eventStart.atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant end = eventEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            page = calendarEventRepository.findByUserIdAndEventStartGreaterThanEqualAndEventStartLessThan(
                    user.userId(), start, end, pageable);
        } else if (eventStart != null) {
            Instant start = eventStart.atStartOfDay(ZoneOffset.UTC).toInstant();
            page = calendarEventRepository.findByUserIdAndEventStartGreaterThanEqual(
                    user.userId(), start, pageable);
        } else if (eventEnd != null) {
            Instant end = eventEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            page = calendarEventRepository.findByUserIdAndEventStartLessThan(
                    user.userId(), end, pageable);
        } else {
            page = calendarEventRepository.findByUserId(user.userId(), pageable);
        }
        return ResponseEntity.ok(page.map(EventResponse::from));
    }

    @GetMapping("/status")
    @Operation(summary = "Status da integração Google", description = "Retorna o estado atual da integração (SYNCED, REAUTH_REQUIRED, etc.)")
    public ResponseEntity<IntegrationStatusResponse> getIntegrationStatus(
            @AuthenticationPrincipal AuthenticatedUser user) {
        return syncStateRepository.findByUserId(user.userId())
                .map(state -> ResponseEntity.ok(new IntegrationStatusResponse(
                        state.getStatus().name(),
                        state.getLastSyncAt() != null ? state.getLastSyncAt().toString() : null,
                        state.getErrorCategory(),
                        state.getErrorMessage()
                )))
                .orElse(ResponseEntity.ok(new IntegrationStatusResponse(
                        SyncStatus.NEVER_SYNCED.name(), null, null, null
                )));
    }

    public record SyncResponse(int created, int updated, int deleted) {}

    public record EventResponse(
            Long id, String googleEventId, String title,
            String eventStart, String eventEnd,
            boolean identified, String serviceDescription,
            BigDecimal serviceValue, String paymentType
    ) {
        static EventResponse from(CalendarEvent e) {
            return new EventResponse(
                    e.getId(), e.getGoogleEventId(), e.getTitle(),
                    e.getEventStart().toString(),
                    e.getEventEnd() != null ? e.getEventEnd().toString() : null,
                    e.isIdentified(),
                    e.getServiceDescriptionSnapshot(),
                    e.getServiceValueSnapshot(),
                    e.getPaymentType() != null ? e.getPaymentType().name() : null
            );
        }
    }

    public record IntegrationStatusResponse(
            String status, String lastSyncAt,
            String errorCategory, String errorMessage
    ) {}
}
