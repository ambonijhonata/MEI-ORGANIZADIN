package com.api.calendar;

record CalendarIntegrationStatusReadModel(
        String status,
        String lastSyncAt,
        String errorCategory,
        String errorMessage
) {
}
