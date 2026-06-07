package com.api.calendar;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "calendar_event_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEventPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "calendar_event_id", nullable = false)
    private CalendarEvent calendarEvent;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private PaymentType paymentType;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "value_total", nullable = false)
    private boolean coversTotalValue;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public CalendarEventPayment(
            final CalendarEvent calendarEvent,
            final PaymentType paymentType,
            final BigDecimal amount,
            final boolean valueTotal,
            final Instant paidAt
    ) {
        relinkTo(calendarEvent);
        refreshDetails(paymentType, amount, valueTotal, paidAt);
    }

    @PrePersist
    protected void prePersist() {
        this.createdAt = Instant.now();
    }

    public CalendarPaymentEntryReadModel toReadModel() {
        return new CalendarPaymentEntryReadModel(
                paymentId,
                paymentTypeName(),
                amount,
                coversTotalValue,
                paidAtText()
        );
    }

    public Long getId() {
        return paymentId;
    }

    public boolean isValueTotal() {
        return coversTotalValue;
    }

    public final void relinkTo(final CalendarEvent event) {
        this.calendarEvent = event;
    }

    public final void refreshDetails(final PaymentType type,
                                     final BigDecimal paidAmount,
                                     final boolean totalValuePayment,
                                     final Instant paidTimestamp) {
        this.paymentType = type;
        this.amount = paidAmount;
        this.coversTotalValue = totalValuePayment;
        this.paidAt = paidTimestamp;
    }

    public boolean isPaid() {
        return paidAt != null;
    }

    public BigDecimal amountOrZero() {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    public String paymentTypeName() {
        return paymentType != null ? paymentType.name() : null;
    }

    public String paidAtText() {
        return paidAt != null ? paidAt.toString() : null;
    }
}
