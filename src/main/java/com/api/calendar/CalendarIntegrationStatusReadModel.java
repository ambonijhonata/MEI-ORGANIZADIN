package com.api.calendar;

public record CalendarIntegrationStatusReadModel(
        String status,
        String lastSyncAt,
        String errorCategory,
        String errorMessage
) {
}
