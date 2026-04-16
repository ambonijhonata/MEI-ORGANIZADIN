package com.api.calendar;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalendarPaymentSummaryTest {

    @Test
    void shouldClassifyNoPaymentAsNone() {
        CalendarPaymentSummary summary = CalendarPaymentSummary.of(BigDecimal.ZERO, new BigDecimal("50.00"));

        assertEquals(CalendarPaymentStatus.NONE, summary.status());
        assertEquals(new BigDecimal("0"), summary.paidAmount());
        assertEquals(new BigDecimal("50.00"), summary.totalAmount());
    }

    @Test
    void shouldClassifyPartialPaymentAsPartial() {
        CalendarPaymentSummary summary = CalendarPaymentSummary.of(new BigDecimal("20.00"), new BigDecimal("50.00"));

        assertEquals(CalendarPaymentStatus.PARTIAL, summary.status());
        assertEquals(new BigDecimal("20.00"), summary.paidAmount());
        assertEquals(new BigDecimal("50.00"), summary.totalAmount());
    }

    @Test
    void shouldClassifyFullPaymentAsPaid() {
        CalendarPaymentSummary summary = CalendarPaymentSummary.of(new BigDecimal("50.00"), new BigDecimal("50.00"));

        assertEquals(CalendarPaymentStatus.PAID, summary.status());
        assertEquals(new BigDecimal("50.00"), summary.paidAmount());
        assertEquals(new BigDecimal("50.00"), summary.totalAmount());
    }
}
