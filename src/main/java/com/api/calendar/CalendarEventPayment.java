package com.api.calendar;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "calendar_event_payments")
public class CalendarEventPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_event_id", nullable = false)
    private CalendarEvent calendarEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private PaymentType paymentType;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "value_total", nullable = false)
    private boolean valueTotal;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CalendarEventPayment() {
    }

    public CalendarEventPayment(
            CalendarEvent calendarEvent,
            PaymentType paymentType,
            BigDecimal amount,
            boolean valueTotal,
            Instant paidAt
    ) {
        this.calendarEvent = calendarEvent;
        this.paymentType = paymentType;
        this.amount = amount;
        this.valueTotal = valueTotal;
        this.paidAt = paidAt;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public CalendarEvent getCalendarEvent() {
        return calendarEvent;
    }

    public PaymentType getPaymentType() {
        return paymentType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public boolean isValueTotal() {
        return valueTotal;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
