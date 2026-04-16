package com.api.calendar;

import java.math.BigDecimal;

public record CalendarPaymentSummary(BigDecimal paidAmount, BigDecimal totalAmount, CalendarPaymentStatus status) {

    public static CalendarPaymentSummary of(BigDecimal paidAmount, BigDecimal totalAmount) {
        BigDecimal safePaidAmount = paidAmount != null ? paidAmount : BigDecimal.ZERO;
        BigDecimal safeTotalAmount = totalAmount != null ? totalAmount : BigDecimal.ZERO;

        CalendarPaymentStatus status;
        if (safeTotalAmount.compareTo(BigDecimal.ZERO) <= 0 || safePaidAmount.compareTo(BigDecimal.ZERO) <= 0) {
            status = CalendarPaymentStatus.NONE;
        } else if (safePaidAmount.compareTo(safeTotalAmount) < 0) {
            status = CalendarPaymentStatus.PARTIAL;
        } else {
            status = CalendarPaymentStatus.PAID;
        }

        return new CalendarPaymentSummary(safePaidAmount, safeTotalAmount, status);
    }
}
