package com.api.report;

import com.api.calendar.CalendarEvent;
import com.api.calendar.CalendarEventPaymentMethodTotal;
import com.api.calendar.CalendarEventPaymentRepository;
import com.api.calendar.CalendarEventRepository;
import com.api.calendar.PaymentType;
import com.api.calendar.SyncState;
import com.api.calendar.SyncStateRepository;
import com.api.calendar.SyncStatus;
import com.api.common.InvalidPeriodException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentMethodRevenueReportService {

    private static final int CURRENCY_SCALE = 2;
    private static final List<PaymentType> CANONICAL_PAYMENT_ORDER = List.of(
            PaymentType.DINHEIRO,
            PaymentType.PIX,
            PaymentType.DEBITO,
            PaymentType.CREDITO
    );

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventPaymentRepository calendarEventPaymentRepository;
    private final SyncStateRepository syncStateRepository;
    private final long freshnessMinutes;

    public PaymentMethodRevenueReportService(
            CalendarEventRepository calendarEventRepository,
            CalendarEventPaymentRepository calendarEventPaymentRepository,
            SyncStateRepository syncStateRepository,
            @Value("${sync.freshness-minutes}") long freshnessMinutes
    ) {
        this.calendarEventRepository = calendarEventRepository;
        this.calendarEventPaymentRepository = calendarEventPaymentRepository;
        this.syncStateRepository = syncStateRepository;
        this.freshnessMinutes = freshnessMinutes;
    }

    public PaymentMethodRevenueReport generateReport(Long userId, LocalDate startDate, LocalDate endDate) {
        validatePeriod(startDate, endDate, 12);

        Instant startInstant = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Long> eventIds = calendarEventRepository.findIdentifiedByUserAndPeriod(userId, startInstant, endInstant)
                .stream()
                .map(CalendarEvent::getId)
                .toList();

        Map<PaymentType, BigDecimal> totalsByType = new EnumMap<>(PaymentType.class);
        for (PaymentType paymentType : CANONICAL_PAYMENT_ORDER) {
            totalsByType.put(paymentType, zeroAmount());
        }

        if (!eventIds.isEmpty()) {
            for (CalendarEventPaymentMethodTotal total : calendarEventPaymentRepository
                    .summarizePaidAmountsByPaymentTypeForEventIdIn(eventIds)) {
                if (total.paymentType() != null) {
                    totalsByType.put(total.paymentType(), normalizeAmount(total.paidAmount()));
                }
            }
        }

        List<PaymentMethodRevenueEntry> entries = CANONICAL_PAYMENT_ORDER.stream()
                .map(paymentType -> new PaymentMethodRevenueEntry(
                        paymentType.name(),
                        totalsByType.getOrDefault(paymentType, zeroAmount())
                ))
                .toList();

        return new PaymentMethodRevenueReport(
                entries,
                startDate,
                endDate,
                buildSyncMetadata(userId)
        );
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate, int maxMonths) {
        if (startDate.isAfter(endDate)) {
            throw new InvalidPeriodException("Start date must be before or equal to end date");
        }
        long monthsBetween = ChronoUnit.MONTHS.between(startDate, endDate);
        if (monthsBetween > maxMonths) {
            throw new InvalidPeriodException("Period must not exceed " + maxMonths + " months");
        }
    }

    private RevenueReportService.SyncMetadata buildSyncMetadata(Long userId) {
        return syncStateRepository.findByUserId(userId)
                .map(state -> {
                    boolean dataUpToDate = isDataUpToDate(state);
                    boolean reauthRequired = state.getStatus() == SyncStatus.REAUTH_REQUIRED;
                    return new RevenueReportService.SyncMetadata(dataUpToDate, state.getLastSyncAt(), reauthRequired);
                })
                .orElse(new RevenueReportService.SyncMetadata(false, null, false));
    }

    private boolean isDataUpToDate(SyncState state) {
        if (state.getLastSyncAt() == null) {
            return false;
        }
        Instant threshold = Instant.now().minus(freshnessMinutes, ChronoUnit.MINUTES);
        return state.getLastSyncAt().isAfter(threshold);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            return zeroAmount();
        }
        return amount.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroAmount() {
        return BigDecimal.ZERO.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
    }

    public record PaymentMethodRevenueReport(
            List<PaymentMethodRevenueEntry> entries,
            LocalDate startDate,
            LocalDate endDate,
            RevenueReportService.SyncMetadata syncMetadata
    ) {
    }

    public record PaymentMethodRevenueEntry(String paymentType, BigDecimal total) {
    }
}
