package com.api.calendar;

import java.math.BigDecimal;

record CalendarEventReadModel(
        Long entityId,
        String googleEventId,
        String title,
        String eventStart,
        String eventEnd,
        boolean identified,
        String serviceDesc,
        BigDecimal serviceValue,
        String paymentType,
        BigDecimal paidAmount
) {
}
