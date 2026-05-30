package com.api.calendar;

import com.api.common.BusinessException;
import com.api.common.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"PMD.AvoidLiteralsInIfCondition", "PMD.CyclomaticComplexity", "PMD.LawOfDemeter", "PMD.LongVariable", "PMD.OnlyOneReturn"})
@Component
public class CalendarPaymentService {

    private static final int MAX_PAYMENTS = 4;

    private final CalendarEventRepository calendarEventRepository;

    public CalendarPaymentService(final CalendarEventRepository calendarEventRepository) {
        this.calendarEventRepository = calendarEventRepository;
    }

    @Transactional
    public List<CalendarEventPayment> upsertPayments(
            final Long userId,
            final Long eventId,
            final List<PaymentInput> requestedPayments
    ) {
        final CalendarEvent event = calendarEventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar event not found"));

        if (requestedPayments == null) {
            throw new BusinessException("Payments payload is required");
        }

        if (requestedPayments.isEmpty()) {
            event.clearPayments();
            final CalendarEvent savedEvent = calendarEventRepository.save(event);
            return materializePayments(savedEvent);
        }

        validateRequest(event, requestedPayments);

        final BigDecimal totalServiceValue = event.getServiceValueSnapshot() != null
                ? event.getServiceValueSnapshot()
                : BigDecimal.ZERO;

        List<PaymentInput> effectivePayments = requestedPayments;
        final int totalValueCheckedCount = (int) requestedPayments.stream().filter(PaymentInput::valueTotal).count();
        if (totalValueCheckedCount == 1) {
            final PaymentInput totalValuePayment = requestedPayments.stream()
                    .filter(PaymentInput::valueTotal)
                    .findFirst()
                    .orElseThrow();
            effectivePayments = List.of(new PaymentInput(
                    totalValuePayment.paymentType(),
                    totalServiceValue,
                    true
            ));
        }

        final List<CalendarEventPayment> entities = new ArrayList<>();
        for (final PaymentInput input : effectivePayments) {
            entities.add(new CalendarEventPayment(
                    event,
                    input.paymentType(),
                    input.amount(),
                    input.valueTotal(),
                    Instant.now()
            ));
        }

        event.replacePayments(entities);
        final CalendarEvent savedEvent = calendarEventRepository.save(event);
        return materializePayments(savedEvent);
    }

    @Transactional(readOnly = true)
    public List<CalendarEventPayment> listPayments(final Long userId, final Long eventId) {
        final CalendarEvent event = calendarEventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Calendar event not found"));
        return materializePayments(event);
    }

    private void validateRequest(final CalendarEvent event, final List<PaymentInput> requestedPayments) {
        if (requestedPayments.size() > MAX_PAYMENTS) {
            throw new BusinessException("At most 4 payment entries are allowed");
        }

        final long totalValueCount = requestedPayments.stream().filter(PaymentInput::valueTotal).count();
        if (totalValueCount > 1) {
            throw new BusinessException("Only one payment entry can be marked as total value");
        }

        if (totalValueCount == 1) {
            final PaymentInput totalValuePayment = requestedPayments.stream()
                    .filter(PaymentInput::valueTotal)
                    .findFirst()
                    .orElseThrow();
            if (totalValuePayment.paymentType() == null) {
                throw new BusinessException("Payment type is required");
            }
            return;
        }

        final BigDecimal totalServiceValue = event.getServiceValueSnapshot() != null
                ? event.getServiceValueSnapshot()
                : BigDecimal.ZERO;

        BigDecimal sum = BigDecimal.ZERO;
        for (final PaymentInput payment : requestedPayments) {
            if (payment.paymentType() == null) {
                throw new BusinessException("Payment type is required");
            }
            if (payment.amount() == null || payment.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("Payment amount must be greater than zero");
            }
            sum = sum.add(payment.amount());
        }

        if (sum.compareTo(totalServiceValue) > 0) {
            throw new BusinessException("Payment composition must not exceed total service value");
        }
    }

    private List<CalendarEventPayment> materializePayments(final CalendarEvent event) {
        return new ArrayList<>(event.getPayments());
    }

    public record PaymentInput(PaymentType paymentType, BigDecimal amount, boolean valueTotal) {}
}
