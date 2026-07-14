package com.api.report;

import com.api.calendar.CalendarEvent;
import com.api.calendar.CalendarEventPaymentMethodTotal;
import com.api.calendar.CalendarEventPaymentRepository;
import com.api.calendar.CalendarEventRepository;
import com.api.calendar.PaymentType;
import com.api.calendar.SyncStateRepository;
import com.api.calendar.SyncStateReportMetadataFactory;
import com.api.common.InvalidPeriodException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentMethodRevenueReportService {

    private static final int CURRENCY_SCALE = 2;
    private static final int MAX_MONTHS = 12;
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
    private static final RevenueReportService.SyncMetadata EMPTY_SYNC_META =
            new RevenueReportService.SyncMetadata(false, null, false);
    private static final List<PaymentType> TYPE_ORDER = List.of(
            PaymentType.DINHEIRO,
            PaymentType.PIX,
            PaymentType.DEBITO,
            PaymentType.CREDITO
    );

    private final CalendarEventRepository eventRepo;
    private final CalendarEventPaymentRepository paymentRepo;
    private final SyncStateRepository syncRepo;
    private final long freshMins;

    public PaymentMethodRevenueReportService(
            final CalendarEventRepository eventRepo,
            final CalendarEventPaymentRepository paymentRepo,
            final SyncStateRepository syncRepo,
            @Value("${sync.freshness-minutes}") final long freshMins
    ) {
        this.eventRepo = eventRepo;
        this.paymentRepo = paymentRepo;
        this.syncRepo = syncRepo;
        this.freshMins = freshMins;
    }

    public PaymentMethodRevenueReport generateReport(final Long userId, final LocalDate startDate, final LocalDate endDate) {
        validatePeriod(startDate, endDate);

        final List<Long> eventIds = loadEventIds(userId, startDate, endDate);
        final Map<PaymentType, BigDecimal> totalsByType = loadTotalsByType(eventIds);
        final List<PaymentMethodRevenueEntry> entries = buildEntries(totalsByType);

        return new PaymentMethodRevenueReport(
                entries,
                startDate,
                endDate,
                buildSyncMeta(userId)
        );
    }

    private List<Long> loadEventIds(final Long userId, final LocalDate startDate, final LocalDate endDate) {
        final Instant startAt = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        final Instant endAt = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        final List<CalendarEvent> events = eventRepo.findIdentifiedByUserAndPeriod(userId, startAt, endAt);
        return events.stream()
                .map(CalendarEvent::getId)
                .toList();
    }

    private Map<PaymentType, BigDecimal> loadTotalsByType(final List<Long> eventIds) {
        final Map<PaymentType, BigDecimal> totalsByType = initTotals();
        if (!eventIds.isEmpty()) {
            mergePaidTotals(eventIds, totalsByType);
        }
        return totalsByType;
    }

    private Map<PaymentType, BigDecimal> initTotals() {
        final Map<PaymentType, BigDecimal> totalsByType = new EnumMap<>(PaymentType.class);
        for (final PaymentType paymentType : TYPE_ORDER) {
            totalsByType.put(paymentType, ZERO_AMOUNT);
        }
        return totalsByType;
    }

    private void mergePaidTotals(final List<Long> eventIds, final Map<PaymentType, BigDecimal> totalsByType) {
        final List<CalendarEventPaymentMethodTotal> paidTotals =
                paymentRepo.summarizePaidAmountsByPaymentTypeForEventIdIn(eventIds);
        for (final CalendarEventPaymentMethodTotal paidTotal : paidTotals) {
            final PaymentType paymentType = paidTotal.paymentType();
            if (paymentType != null) {
                totalsByType.put(paymentType, normalizeAmount(paidTotal.paidAmount()));
            }
        }
    }

    private List<PaymentMethodRevenueEntry> buildEntries(final Map<PaymentType, BigDecimal> totalsByType) {
        return TYPE_ORDER.stream()
                .map(paymentType -> new PaymentMethodRevenueEntry(
                        paymentType.name(),
                        totalsByType.getOrDefault(paymentType, ZERO_AMOUNT)
                ))
                .toList();
    }

    private void validatePeriod(final LocalDate startDate, final LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new InvalidPeriodException("Start date must be before or equal to end date");
        }
        final long monthsBetween = ChronoUnit.MONTHS.between(startDate, endDate);
        if (monthsBetween > MAX_MONTHS) {
            throw new InvalidPeriodException("Period must not exceed " + MAX_MONTHS + " months");
        }
    }

    private RevenueReportService.SyncMetadata buildSyncMeta(final Long userId) {
        final Instant threshold = Instant.now().minus(freshMins, ChronoUnit.MINUTES);
        return syncRepo.findByUserId(userId)
                .map(state -> SyncStateReportMetadataFactory.create(state, threshold))
                .orElse(EMPTY_SYNC_META);
    }

    private BigDecimal normalizeAmount(final BigDecimal amount) {
        BigDecimal normalizedAmount = ZERO_AMOUNT;
        if (amount != null) {
            normalizedAmount = amount.setScale(CURRENCY_SCALE, RoundingMode.HALF_UP);
        }
        return normalizedAmount;
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
