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

@SuppressWarnings({"PMD.LawOfDemeter", "PMD.LongVariable", "PMD.OnlyOneReturn"})
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
            final CalendarEventRepository calendarEventRepository,
            final CalendarEventPaymentRepository calendarEventPaymentRepository,
            final SyncStateRepository syncStateRepository,
            @Value("${sync.freshness-minutes}") final long freshnessMinutes
    ) {
        this.calendarEventRepository = calendarEventRepository;
        this.calendarEventPaymentRepository = calendarEventPaymentRepository;
        this.syncStateRepository = syncStateRepository;
        this.freshnessMinutes = freshnessMinutes;
    }

    public PaymentMethodRevenueReport generateReport(final Long userId, final LocalDate startDate, final LocalDate endDate) {
        validatePeriod(startDate, endDate, 12);

        final Instant startInstant = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        final Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        final List<Long> eventIds = calendarEventRepository.findIdentifiedByUserAndPeriod(userId, startInstant, endInstant)
                .stream()
                .map(CalendarEvent::getId)
                .toList();

        final Map<PaymentType, BigDecimal> totalsByType = new EnumMap<>(PaymentType.class);
        for (final PaymentType paymentType : CANONICAL_PAYMENT_ORDER) {
            totalsByType.put(paymentType, zeroAmount());
        }

        if (!eventIds.isEmpty()) {
            for (final CalendarEventPaymentMethodTotal total : calendarEventPaymentRepository
                    .summarizePaidAmountsByPaymentTypeForEventIdIn(eventIds)) {
                if (total.paymentType() != null) {
                    totalsByType.put(total.paymentType(), normalizeAmount(total.paidAmount()));
                }
            }
        }

        final List<PaymentMethodRevenueEntry> entries = CANONICAL_PAYMENT_ORDER.stream()
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

    private void validatePeriod(final LocalDate startDate, final LocalDate endDate, final int maxMonths) {
        if (startDate.isAfter(endDate)) {
            throw new InvalidPeriodException("Start date must be before or equal to end date");
        }
        final long monthsBetween = ChronoUnit.MONTHS.between(startDate, endDate);
        if (monthsBetween > maxMonths) {
            throw new InvalidPeriodException("Period must not exceed " + maxMonths + " months");
        }
    }

    private RevenueReportService.SyncMetadata buildSyncMetadata(final Long userId) {
        return syncStateRepository.findByUserId(userId)
                .map(state -> {
                    final boolean dataUpToDate = isDataUpToDate(state);
                    final boolean reauthRequired = state.getStatus() == SyncStatus.REAUTH_REQUIRED;
                    return new RevenueReportService.SyncMetadata(dataUpToDate, state.getLastSyncAt(), reauthRequired);
                })
                .orElse(new RevenueReportService.SyncMetadata(false, null, false));
    }

    private boolean isDataUpToDate(final SyncState state) {
        if (state.getLastSyncAt() == null) {
            return false;
        }
        final Instant threshold = Instant.now().minus(freshnessMinutes, ChronoUnit.MINUTES);
        return state.getLastSyncAt().isAfter(threshold);
    }

    private BigDecimal normalizeAmount(final BigDecimal amount) {
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
