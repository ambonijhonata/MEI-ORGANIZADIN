package com.api.report;

import com.api.calendar.*;
import com.api.common.InvalidPeriodException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"PMD.ControlStatementBraces", "PMD.LawOfDemeter", "PMD.LongVariable", "PMD.OnlyOneReturn"})
@Component
public class RevenueReportService {

    private final CalendarEventRepository calendarEventRepository;
    private final ReportPaidAmountService reportPaidAmountService;
    private final SyncStateRepository syncStateRepository;
    private final long freshnessMinutes;

    public RevenueReportService(final CalendarEventRepository calendarEventRepository,
                                 final ReportPaidAmountService reportPaidAmountService,
                                 final SyncStateRepository syncStateRepository,
                                 @Value("${sync.freshness-minutes}") final long freshnessMinutes) {
        this.calendarEventRepository = calendarEventRepository;
        this.reportPaidAmountService = reportPaidAmountService;
        this.syncStateRepository = syncStateRepository;
        this.freshnessMinutes = freshnessMinutes;
    }

    public RevenueReport generateReport(final Long userId, final LocalDate startDate, final LocalDate endDate) {
        return generateReport(userId, startDate, endDate, PaymentScope.ALL);
    }

    public RevenueReport generateReport(final Long userId, final LocalDate startDate, final LocalDate endDate, final PaymentScope paymentScope) {
        validatePeriod(startDate, endDate, 12);

        final Instant startInstant = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        final Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        final List<CalendarEvent> events = calendarEventRepository.findIdentifiedByUserAndPeriod(userId, startInstant, endInstant);

        final BigDecimal totalRevenue;
        if (paymentScope == PaymentScope.PAID_ONLY) {
            final List<Long> eventIds = events.stream().map(CalendarEvent::getId).toList();
            final Map<Long, BigDecimal> paidAmountsByEventId = reportPaidAmountService.loadPaidAmountsByEventId(eventIds);
            totalRevenue = events.stream()
                    .map(event -> reportPaidAmountService.resolvePaidOnlyEventAmount(
                            event,
                            safeAmount(event.getServiceValueSnapshot()),
                            paidAmountsByEventId
                    ))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } else {
            totalRevenue = events.stream()
                    .map(event -> safeAmount(event.getServiceValueSnapshot()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        final SyncMetadata metadata = buildSyncMetadata(userId);

        return new RevenueReport(totalRevenue, startDate, endDate, metadata);
    }

    private BigDecimal safeAmount(final BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
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

    private SyncMetadata buildSyncMetadata(final Long userId) {
        return syncStateRepository.findByUserId(userId)
                .map(state -> {
                    final boolean dataUpToDate = isDataUpToDate(state);
                    final boolean reauthRequired = state.getStatus() == SyncStatus.REAUTH_REQUIRED;
                    return new SyncMetadata(dataUpToDate, state.getLastSyncAt(), reauthRequired);
                })
                .orElse(new SyncMetadata(false, null, false));
    }

    private boolean isDataUpToDate(final SyncState state) {
        if (state.getLastSyncAt() == null) return false;
        final Instant threshold = Instant.now().minus(freshnessMinutes, ChronoUnit.MINUTES);
        return state.getLastSyncAt().isAfter(threshold);
    }

    public record RevenueReport(BigDecimal totalRevenue, LocalDate startDate, LocalDate endDate,
                                  SyncMetadata syncMetadata) {}

    public record SyncMetadata(boolean dataUpToDate, Instant lastSyncAt, boolean reauthRequired) {}
}
