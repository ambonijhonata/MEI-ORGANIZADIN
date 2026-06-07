package com.api.calendar;

import com.api.common.BusinessException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class CalendarFacade {
    private final CalendarSyncService syncService;
    private final CalendarPaymentService paymentService;
    private final ManualAppointmentService apptService;
    private final CalendarEventQueryService eventQueryService;

    public CalendarFacade(
            final CalendarSyncService syncService,
            final CalendarPaymentService paymentService,
            final ManualAppointmentService apptService,
            final CalendarEventQueryService eventQueryService
    ) {
        this.syncService = syncService;
        this.paymentService = paymentService;
        this.apptService = apptService;
        this.eventQueryService = eventQueryService;
    }

    public CalendarApiModels.SyncResponse triggerSync(final Long userId, final LocalDate startDate) {
        final CalendarSyncService.SyncResult result = syncService.synchronize(userId, startDate);
        return CalendarApiMapper.toSyncResponse(result);
    }

    public CalendarApiModels.EventResponse createManualAppointment(
            final Long userId,
            final CalendarApiModels.ManualAppointmentCreateRequest request
    ) {
        final CalendarEvent created = apptService.createManualAppointment(
                userId,
                new ManualAppointmentService.ManualAppointmentRequest(
                        request.clientId(),
                        request.appointmentDate(),
                        request.startTime(),
                        request.endTime(),
                        request.serviceIds()
                )
        );
        return CalendarApiMapper.toEventResponse(created.toReadModel(null));
    }

    public Page<CalendarApiModels.EventResponse> listEvents(
            final Long userId,
            final LocalDate eventStart,
            final LocalDate eventEnd,
            final Pageable pageable
    ) {
        return eventQueryService.listEvents(userId, eventStart, eventEnd, pageable)
                .map(CalendarApiMapper::toEventResponse);
    }

    public CalendarApiModels.IntegrationStatusResponse getIntegrationStatus(final Long userId) {
        return CalendarApiMapper.toIntegrationStatus(eventQueryService.getIntegrationStatus(userId));
    }

    public CalendarApiModels.PaymentsResponse upsertPayments(
            final Long userId,
            final Long eventId,
            final CalendarApiModels.PaymentsUpsertRequest request
    ) {
        final List<CalendarApiModels.PaymentEntryRequest> requestPayments = request != null && request.payments() != null
                ? request.payments()
                : List.of();
        final List<CalendarEventPayment> savedPayments = paymentService.upsertPayments(
                userId,
                eventId,
                requestPayments.stream()
                        .map(entry -> new CalendarPaymentService.PaymentInput(
                                parsePaymentType(entry.paymentType()),
                                entry.amount(),
                                entry.valueTotal()
                        ))
                        .toList()
        );
        return new CalendarApiModels.PaymentsResponse(
                eventId,
                savedPayments.stream()
                        .map(CalendarEventPayment::toReadModel)
                        .map(CalendarApiMapper::toPaymentEntryResponse)
                        .toList()
        );
    }

    public CalendarApiModels.PaymentsResponse getPayments(final Long userId, final Long eventId) {
        final List<CalendarEventPayment> payments = paymentService.listPayments(userId, eventId);
        return new CalendarApiModels.PaymentsResponse(
                eventId,
                payments.stream()
                        .map(CalendarEventPayment::toReadModel)
                        .map(CalendarApiMapper::toPaymentEntryResponse)
                        .toList()
        );
    }

    private PaymentType parsePaymentType(final String rawPaymentType) {
        try {
            return PaymentType.valueOf(rawPaymentType);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid payment type: " + rawPaymentType, ex);
        }
    }
}
