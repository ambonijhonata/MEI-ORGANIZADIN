package com.api.calendar;

import java.util.List;

final class CalendarApiMapper {
    private CalendarApiMapper() {
    }

    public static CalendarApiModels.SyncResponse toSyncResponse(final CalendarSyncService.SyncResult result) {
        return new CalendarApiModels.SyncResponse(result.created(), result.updated(), result.deleted());
    }

    public static CalendarApiModels.EventResponse toEventResponse(final CalendarEventReadModel event) {
        return new CalendarApiModels.EventResponse(
                event.entityId(),
                event.googleEventId(),
                event.title(),
                event.eventStart(),
                event.eventEnd(),
                event.identified(),
                event.serviceDesc(),
                event.serviceValue(),
                event.paymentType(),
                List.of(),
                CalendarPaymentSummary.fromAmounts(event.paidAmount(), event.serviceValue())
        );
    }

    public static CalendarApiModels.IntegrationStatusResponse toIntegrationStatus(
            final CalendarIntegrationStatusReadModel status
    ) {
        return new CalendarApiModels.IntegrationStatusResponse(
                status.status(),
                status.lastSyncAt(),
                status.errorCategory(),
                status.errorMessage()
        );
    }

    public static CalendarApiModels.PaymentEntryResponse toPaymentEntryResponse(
            final CalendarPaymentEntryReadModel payment
    ) {
        return new CalendarApiModels.PaymentEntryResponse(
                payment.paymentId(),
                payment.paymentType(),
                payment.amount(),
                payment.valueTotal(),
                payment.paidAt()
        );
    }
}
