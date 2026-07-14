package com.api.report;

import com.api.calendar.CalendarEvent;
import com.api.calendar.CalendarEventPaymentRepository;
import com.api.calendar.CalendarEventPaymentTotal;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReportPaidAmountService {

    private final CalendarEventPaymentRepository payRepo;

    public ReportPaidAmountService(final CalendarEventPaymentRepository payRepo) {
        this.payRepo = payRepo;
    }

    public Map<Long, BigDecimal> loadPaidAmountsByEventId(final Collection<Long> eventIds) {
        final Map<Long, BigDecimal> paidByEvent = new HashMap<>();
        if (hasEventIds(eventIds)) {
            mergePaidAmounts(eventIds, paidByEvent);
        }
        return paidByEvent.isEmpty() ? Map.of() : paidByEvent;
    }

    public BigDecimal resolvePaidOnlyEventAmount(final CalendarEvent event,
                                                 final BigDecimal svcTotal,
                                                 final Map<Long, BigDecimal> paidByEvent) {
        final BigDecimal explicitAmt = paidByEvent.get(event.getId());
        BigDecimal resolvedAmt = BigDecimal.ZERO;
        if (explicitAmt != null) {
            resolvedAmt = explicitAmt;
        } else if (event.getPaymentType() != null) {
            resolvedAmt = ReportPaidAmountAllocator.normalize(svcTotal);
        }
        return resolvedAmt;
    }

    public List<BigDecimal> distributeAmountProportionally(final BigDecimal amount, final List<BigDecimal> weights) {
        return ReportPaidAmountAllocator.distribute(amount, weights);
    }

    private boolean hasEventIds(final Collection<Long> eventIds) {
        return eventIds != null && !eventIds.isEmpty();
    }

    private void mergePaidAmounts(final Collection<Long> eventIds, final Map<Long, BigDecimal> paidByEvent) {
        final List<CalendarEventPaymentTotal> paidTotals = payRepo.summarizePaidAmountsByEventIdIn(eventIds);
        for (final CalendarEventPaymentTotal paidTotal : paidTotals) {
            paidByEvent.put(paidTotal.eventId(), ReportPaidAmountAllocator.normalize(paidTotal.paidAmount()));
        }
    }
}
