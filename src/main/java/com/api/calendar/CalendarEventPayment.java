package com.api.calendar;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

@SuppressWarnings({"PMD.CommentDefaultAccessModifier", "PMD.DataClass", "PMD.ShortVariable"})
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
            final CalendarEvent calendarEvent,
            final PaymentType paymentType,
            final BigDecimal amount,
            final boolean valueTotal,
            final Instant paidAt
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

    public CalendarPaymentEntryReadModel toReadModel() {
        final String paymentTypeName = paymentType != null ? paymentType.name() : null;
        return new CalendarPaymentEntryReadModel(
                id,
                paymentTypeName,
                amount,
                valueTotal,
                paidAt != null ? paidAt.toString() : null
        );
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
