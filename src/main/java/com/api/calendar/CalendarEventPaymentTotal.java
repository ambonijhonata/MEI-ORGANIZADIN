package com.api.calendar;

import java.math.BigDecimal;

public record CalendarEventPaymentTotal(Long eventId, BigDecimal paidAmount) {
}
