package com.api.calendar;

import java.math.BigDecimal;

public record CalendarEventPaymentMethodTotal(PaymentType paymentType, BigDecimal paidAmount) {
}
