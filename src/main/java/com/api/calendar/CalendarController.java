package com.api.calendar;

import com.api.auth.AuthenticatedUser;
import com.api.common.BusinessException;
import com.api.common.PageRequestSanitizer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"PMD.AvoidCatchingGenericException", "PMD.CommentDefaultAccessModifier", "PMD.CouplingBetweenObjects", "PMD.FieldNamingConventions", "PMD.GuardLogStatement", "PMD.LawOfDemeter", "PMD.LongVariable", "PMD.OnlyOneReturn", "PMD.PreserveStackTrace", "PMD.ShortVariable"})
@RestController
@RequestMapping("/api/calendar")
@Tag(name = "Google Calendar", description = "Sincronizacao com Google Calendar e consulta de eventos")
public class CalendarController {

    private static final Logger log = LoggerFactory.getLogger(CalendarController.class);
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id",
            "googleEventId",
            "title",
            "eventStart",
            "eventEnd",
            "identified",
            "serviceDescriptionSnapshot",
            "serviceValueSnapshot",
            "paymentType"
    );

    private final CalendarSyncService calendarSyncService;
    private final CalendarPaymentService calendarPaymentService;
    private final ManualAppointmentService manualAppointmentService;
    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventPaymentRepository calendarEventPaymentRepository;
    private final SyncStateRepository syncStateRepository;

    public CalendarController(
            final CalendarSyncService calendarSyncService,
            final CalendarPaymentService calendarPaymentService,
            final ManualAppointmentService manualAppointmentService,
            final CalendarEventRepository calendarEventRepository,
            final CalendarEventPaymentRepository calendarEventPaymentRepository,
            final SyncStateRepository syncStateRepository
    ) {
        this.calendarSyncService = calendarSyncService;
        this.calendarPaymentService = calendarPaymentService;
        this.manualAppointmentService = manualAppointmentService;
        this.calendarEventRepository = calendarEventRepository;
        this.calendarEventPaymentRepository = calendarEventPaymentRepository;
        this.syncStateRepository = syncStateRepository;
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
    public ResponseEntity<SyncResponse> triggerSync(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            @Parameter(
                    description = "Data inicial opcional para sincronizar eventos a partir do dia informado",
                    example = "2026-04-01"
            )
            final LocalDate startDate
    ) {
        final CalendarSyncService.SyncResult result = calendarSyncService.synchronize(user.userId(), startDate);
        return ResponseEntity.ok(new SyncResponse(result.created(), result.updated(), result.deleted()));
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
    public ResponseEntity<EventResponse> createManualAppointment(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @Valid @RequestBody final ManualAppointmentCreateRequest request
    ) {
        final CalendarEvent created = manualAppointmentService.createManualAppointment(
                user.userId(),
                new ManualAppointmentService.ManualAppointmentRequest(
                        request.clientId(),
                        request.appointmentDate(),
                        request.startTime(),
                        request.endTime(),
                        request.serviceIds()
                )
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(EventResponse.from(created, null));
    }

    @GetMapping("/events")
    @Operation(
            summary = "Listar eventos sincronizados",
            description = "Retorna eventos sincronizados, com paginacao e filtros opcionais por periodo"
    )
    public ResponseEntity<Page<EventResponse>> listEvents(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate eventStart,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) final LocalDate eventEnd,
            final Pageable pageable
    ) {
        final Pageable sanitizedPageable = PageRequestSanitizer.sanitizePageable(
                pageable,
                ALLOWED_SORT_FIELDS,
                DEFAULT_PAGE,
                DEFAULT_PAGE_SIZE,
                MAX_PAGE_SIZE
        );
        try {
            final Page<CalendarEvent> page;
            if (eventStart != null && eventEnd != null) {
                final Instant start = eventStart.atStartOfDay(ZoneOffset.UTC).toInstant();
                final Instant end = eventEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                page = calendarEventRepository.findByUserIdAndEventStartGreaterThanEqualAndEventStartLessThan(
                        user.userId(), start, end, sanitizedPageable
                );
            } else if (eventStart != null) {
                final Instant start = eventStart.atStartOfDay(ZoneOffset.UTC).toInstant();
                page = calendarEventRepository.findByUserIdAndEventStartGreaterThanEqual(
                        user.userId(), start, sanitizedPageable
                );
            } else if (eventEnd != null) {
                final Instant end = eventEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                page = calendarEventRepository.findByUserIdAndEventStartLessThan(
                        user.userId(), end, sanitizedPageable
                );
            } else {
                page = calendarEventRepository.findByUserId(user.userId(), sanitizedPageable);
            }

            final Map<Long, BigDecimal> paidAmountsByEventId = loadPaidAmounts(page.getContent());
            return ResponseEntity.ok(page.map(event ->
                    EventResponse.from(event, paidAmountsByEventId.get(event.getId()))
            ));
        } catch (RuntimeException ex) {
            log.error(
                    "calendar_events_list_failed eventStart={} eventEnd={} page={} size={} error_type={}",
                    eventStart,
                    eventEnd,
                    sanitizedPageable.getPageNumber(),
                    sanitizedPageable.getPageSize(),
                    ex.getClass().getSimpleName(),
                    ex
            );
            throw ex;
        }
    }

    @GetMapping("/status")
    @Operation(
            summary = "Status da integracao Google",
            description = "Retorna o estado atual da integracao (SYNCED, REAUTH_REQUIRED, etc.)"
    )
    public ResponseEntity<IntegrationStatusResponse> getIntegrationStatus(
            @AuthenticationPrincipal final AuthenticatedUser user
    ) {
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

    @PatchMapping("/events/{eventId}/payments")
    @Operation(summary = "Registrar composicao de pagamentos do agendamento")
    public ResponseEntity<PaymentsResponse> upsertPayments(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable final Long eventId,
            @RequestBody final PaymentsUpsertRequest request
    ) {
        final List<PaymentEntryRequest> requestPayments = request != null && request.payments() != null
                ? request.payments()
                : Collections.emptyList();
        final List<CalendarEventPayment> savedPayments = calendarPaymentService.upsertPayments(
                user.userId(),
                eventId,
                requestPayments.stream()
                        .map(it -> new CalendarPaymentService.PaymentInput(
                                parsePaymentType(it.paymentType()),
                                it.amount(),
                                it.valueTotal()
                        ))
                        .toList()
        );
        return ResponseEntity.ok(new PaymentsResponse(
                eventId,
                savedPayments.stream().map(PaymentEntryResponse::from).toList()
        ));
    }

    @GetMapping("/events/{eventId}/payments")
    @Operation(summary = "Consultar composicao de pagamentos do agendamento")
    public ResponseEntity<PaymentsResponse> getPayments(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable final Long eventId
    ) {
        final List<CalendarEventPayment> payments = calendarPaymentService.listPayments(user.userId(), eventId);
        return ResponseEntity.ok(new PaymentsResponse(
                eventId,
                payments.stream().map(PaymentEntryResponse::from).toList()
        ));
    }

    public record SyncResponse(int created, int updated, int deleted) {
    }

    public record EventResponse(
            Long id,
            String googleEventId,
            String title,
            String eventStart,
            String eventEnd,
            boolean identified,
            String serviceDescription,
            BigDecimal serviceValue,
            String paymentType,
            List<PaymentEntryResponse> payments,
            CalendarPaymentSummary paymentSummary
    ) {
        static EventResponse from(final CalendarEvent event, final BigDecimal paidAmount) {
            final BigDecimal totalAmount = event.getServiceValueSnapshot() != null
                    ? event.getServiceValueSnapshot()
                    : BigDecimal.ZERO;
            return new EventResponse(
                    event.getId(),
                    event.getGoogleEventId(),
                    event.getTitle(),
                    event.getEventStart().toString(),
                    event.getEventEnd() != null ? event.getEventEnd().toString() : null,
                    event.isIdentified(),
                    event.getServiceDescriptionSnapshot(),
                    totalAmount,
                    event.getPaymentType() != null ? event.getPaymentType().name() : null,
                    Collections.emptyList(),
                    CalendarPaymentSummary.of(paidAmount, totalAmount)
            );
        }
    }

    public record IntegrationStatusResponse(
            String status,
            String lastSyncAt,
            String errorCategory,
            String errorMessage
    ) {
    }

    public record PaymentsUpsertRequest(List<PaymentEntryRequest> payments) {
    }

    public record ManualAppointmentCreateRequest(
            @NotNull Long clientId,
            @NotNull LocalDate appointmentDate,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotEmpty List<@NotNull Long> serviceIds
    ) {
    }

    public record PaymentEntryRequest(String paymentType, BigDecimal amount, boolean valueTotal) {
    }

    public record PaymentsResponse(Long eventId, List<PaymentEntryResponse> payments) {
    }

    public record PaymentEntryResponse(
            Long id,
            String paymentType,
            BigDecimal amount,
            boolean valueTotal,
            String paidAt
    ) {
        static PaymentEntryResponse from(final CalendarEventPayment payment) {
            return new PaymentEntryResponse(
                    payment.getId(),
                    payment.getPaymentType().name(),
                    payment.getAmount(),
                    payment.isValueTotal(),
                    payment.getPaidAt() != null ? payment.getPaidAt().toString() : null
            );
        }
    }

    private PaymentType parsePaymentType(final String rawPaymentType) {
        try {
            return PaymentType.valueOf(rawPaymentType);
        } catch (RuntimeException ex) {
            throw new BusinessException("Invalid payment type: " + rawPaymentType);
        }
    }

    private Map<Long, BigDecimal> loadPaidAmounts(final List<CalendarEvent> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }

        final List<Long> eventIds = events.stream()
                .map(CalendarEvent::getId)
                .toList();

        final Map<Long, BigDecimal> paidAmounts = new HashMap<>();
        for (final CalendarEventPaymentTotal total : calendarEventPaymentRepository.summarizePaidAmountsByEventIdIn(eventIds)) {
            paidAmounts.put(total.eventId(), total.paidAmount());
        }
        return paidAmounts;
    }
}
