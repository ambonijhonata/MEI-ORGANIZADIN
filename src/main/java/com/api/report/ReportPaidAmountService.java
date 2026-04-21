package com.api.report;

import com.api.calendar.CalendarEvent;
import com.api.calendar.CalendarEventPaymentRepository;
import com.api.calendar.CalendarEventPaymentTotal;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReportPaidAmountService {

    private static final int CURRENCY_SCALE = 2;

    private final CalendarEventPaymentRepository calendarEventPaymentRepository;

    public ReportPaidAmountService(CalendarEventPaymentRepository calendarEventPaymentRepository) {
        this.calendarEventPaymentRepository = calendarEventPaymentRepository;
    }

    public Map<Long, BigDecimal> loadPaidAmountsByEventId(Collection<Long> eventIds) {
        if (eventIds == null || eventIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, BigDecimal> paidAmountsByEventId = new HashMap<>();
        for (CalendarEventPaymentTotal total : calendarEventPaymentRepository.summarizePaidAmountsByEventIdIn(eventIds)) {
            paidAmountsByEventId.put(total.eventId(), normalizeAmount(total.paidAmount()));
        }
        return paidAmountsByEventId;
    }

    public BigDecimal resolvePaidOnlyEventAmount(CalendarEvent event,
                                                 BigDecimal eventServiceTotal,
                                                 Map<Long, BigDecimal> paidAmountsByEventId) {
        BigDecimal explicitPaidAmount = paidAmountsByEventId.get(event.getId());
        if (explicitPaidAmount != null) {
            return explicitPaidAmount;
        }
        if (event.getPaymentType() != null) {
            return normalizeAmount(eventServiceTotal);
        }
        return BigDecimal.ZERO;
    }

    public List<BigDecimal> distributeAmountProportionally(BigDecimal amount, List<BigDecimal> weights) {
        if (weights == null || weights.isEmpty()) {
            return List.of();
        }

        BigDecimal targetAmount = normalizeAmount(amount);
        if (targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return zeroAllocation(weights.size());
        }

        BigDecimal totalWeight = weights.stream()
                .map(this::normalizeAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return zeroAllocation(weights.size());
        }

        List<BigDecimal> allocations = new ArrayList<>(weights.size());
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < weights.size(); i++) {
            BigDecimal share;
            if (i == weights.size() - 1) {
                share = targetAmount.subtract(allocated);
            } else {
                BigDecimal weight = normalizeAmount(weights.get(i));
                if (weight.compareTo(BigDecimal.ZERO) <= 0) {
                    share = BigDecimal.ZERO;
                } else {
                    share = targetAmount
                            .multiply(weight)
                            .divide(totalWeight, CURRENCY_SCALE, RoundingMode.HALF_UP);
                }
                allocated = allocated.add(share);
            }
            allocations.add(normalizeAmount(share));
        }

        return allocations;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        }
        return amount.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
    }

    private List<BigDecimal> zeroAllocation(int size) {
        List<BigDecimal> zeros = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            zeros.add(BigDecimal.ZERO.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP));
        }
        return zeros;
    }
}
