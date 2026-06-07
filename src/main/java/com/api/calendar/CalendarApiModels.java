package com.api.calendar;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

public final class CalendarApiModels {
    private CalendarApiModels() {
    }

    public static String typeName() {
        return CalendarApiModels.class.getSimpleName();
    }

    public record SyncResponse(int created, int updated, int deleted) {
    }

    public record EventResponse(
            @JsonProperty("id") Long entityId,
            String googleEventId,
            String title,
            String eventStart,
            String eventEnd,
            boolean identified,
            @JsonProperty("serviceDescription") String serviceDesc,
            BigDecimal serviceValue,
            String paymentType,
            List<PaymentEntryResponse> payments,
            CalendarPaymentSummary paymentSummary
    ) {
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
            Long clientId,
            java.time.LocalDate appointmentDate,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime,
            List<Long> serviceIds
    ) {
    }

    public record PaymentEntryRequest(String paymentType, BigDecimal amount, boolean valueTotal) {
    }

    public record PaymentsResponse(Long eventId, List<PaymentEntryResponse> payments) {
    }

    public record PaymentEntryResponse(
            @JsonProperty("id") Long paymentId,
            String paymentType,
            BigDecimal amount,
            boolean valueTotal,
            String paidAt
    ) {
    }
}
