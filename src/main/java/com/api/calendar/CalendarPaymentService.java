package com.api.calendar;

import com.api.common.BusinessException;
import com.api.common.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class CalendarPaymentService {

    private static final int MAX_PAYMENTS = 4;
    private static final int SINGLE_TOTAL_FLAG = 1;

    private final CalendarEventRepository eventRepository;

    public CalendarPaymentService(final CalendarEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public List<CalendarEventPayment> upsertPayments(
            final Long userId,
            final Long eventId,
            final List<PaymentInput> paymentInputs
    ) {
        final CalendarEvent event = findEvent(userId, eventId);
        if (paymentInputs == null) {
            throw new BusinessException("Payments payload is required");
        }

        final List<CalendarEventPayment> savedPayments;
        if (paymentInputs.isEmpty()) {
            event.clearPayments();
            savedPayments = saveAndMaterialize(event);
        } else {
            validateRequest(event, paymentInputs);
            final List<PaymentInput> effectiveInputs = normalizeEffectiveInputs(event, paymentInputs);
            event.replacePayments(buildPayments(event, effectiveInputs));
            savedPayments = saveAndMaterialize(event);
        }
        return savedPayments;
    }

    @Transactional(readOnly = true)
    public List<CalendarEventPayment> listPayments(final Long userId, final Long eventId) {
        return materializePayments(findEvent(userId, eventId));
    }

    private void validateRequest(final CalendarEvent event, final List<PaymentInput> paymentInputs) {
        if (paymentInputs.size() > MAX_PAYMENTS) {
            throw new BusinessException("At most 4 payment entries are allowed");
        }

        final long totalFlagCount = paymentInputs.stream().filter(PaymentInput::valueTotal).count();
        if (totalFlagCount > SINGLE_TOTAL_FLAG) {
            throw new BusinessException("Only one payment entry can be marked as total value");
        }

        if (totalFlagCount == SINGLE_TOTAL_FLAG) {
            final PaymentInput totalFlagPayment = paymentInputs.stream()
                    .filter(PaymentInput::valueTotal)
                    .findFirst()
                    .orElseThrow();
            if (totalFlagPayment.paymentType() == null) {
                throw new BusinessException("Payment type is required");
            }
        } else {
            validatePartialComposition(event, paymentInputs);
        }
    }

    private void validatePartialComposition(final CalendarEvent event, final List<PaymentInput> paymentInputs) {
        final BigDecimal serviceTotalValue = event.getServiceValueOrZero();
        BigDecimal sum = BigDecimal.ZERO;
        for (final PaymentInput payment : paymentInputs) {
            if (payment.paymentType() == null) {
                throw new BusinessException("Payment type is required");
            }
            if (payment.amount() == null || payment.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("Payment amount must be greater than zero");
            }
            sum = sum.add(payment.amount());
        }

        if (sum.compareTo(serviceTotalValue) > 0) {
            throw new BusinessException("Payment composition must not exceed total service value");
        }
    }

    private List<PaymentInput> normalizeEffectiveInputs(final CalendarEvent event, final List<PaymentInput> paymentInputs) {
        final List<PaymentInput> effectiveInputs;
        if (paymentInputs.stream().filter(PaymentInput::valueTotal).count() == SINGLE_TOTAL_FLAG) {
            final PaymentInput totalFlagPayment = paymentInputs.stream()
                    .filter(PaymentInput::valueTotal)
                    .findFirst()
                    .orElseThrow();
            effectiveInputs = List.of(new PaymentInput(
                    totalFlagPayment.paymentType(),
                    event.getServiceValueOrZero(),
                    true
            ));
        } else {
            effectiveInputs = paymentInputs;
        }
        return effectiveInputs;
    }

    private List<CalendarEventPayment> buildPayments(final CalendarEvent event, final List<PaymentInput> paymentInputs) {
        final List<CalendarEventPayment> payments = new ArrayList<>();
        for (final PaymentInput paymentInput : paymentInputs) {
            payments.add(new CalendarEventPayment(
                    event,
                    paymentInput.paymentType(),
                    paymentInput.amount(),
                    paymentInput.valueTotal(),
                    Instant.now()
            ));
        }
        return payments;
    }

    private List<CalendarEventPayment> saveAndMaterialize(final CalendarEvent event) {
        return materializePayments(eventRepository.save(event));
    }

    private List<CalendarEventPayment> materializePayments(final CalendarEvent event) {
        return new ArrayList<>(event.getPayments());
    }

    private CalendarEvent findEvent(final Long userId, final Long eventId) {
        return eventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar event not found"));
    }

    public record PaymentInput(PaymentType paymentType, BigDecimal amount, boolean valueTotal) {}
}
