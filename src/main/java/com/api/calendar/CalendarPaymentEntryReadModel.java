package com.api.calendar;

import java.math.BigDecimal;

record CalendarPaymentEntryReadModel(
        Long paymentId,
        String paymentType,
        BigDecimal amount,
        boolean valueTotal,
        String paidAt
) {
}
