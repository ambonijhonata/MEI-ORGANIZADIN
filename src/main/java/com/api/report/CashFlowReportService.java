package com.api.report;

import com.api.calendar.CalendarEvent;
import com.api.calendar.CalendarEventRepository;
import com.api.calendar.SyncStateRepository;
import com.api.calendar.SyncStateReportMetadataFactory;
import com.api.common.InvalidPeriodException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CashFlowReportService {

    private static final int MAX_MONTHS = 1;
    private static final RevenueReportService.SyncMetadata EMPTY_SYNC_META =
            new RevenueReportService.SyncMetadata(false, null, false);

    private final CalendarEventRepository eventRepo;
    private final ReportPaidAmountService paidSvc;
    private final SyncStateRepository syncRepo;
    private final long freshMins;

    public CashFlowReportService(final CalendarEventRepository eventRepo,
                                 final ReportPaidAmountService paidSvc,
                                 final SyncStateRepository syncRepo,
                                 @Value("${sync.freshness-minutes}") final long freshMins) {
        this.eventRepo = eventRepo;
        this.paidSvc = paidSvc;
        this.syncRepo = syncRepo;
        this.freshMins = freshMins;
    }

    public CashFlowReport generateReport(final Long userId, final LocalDate startDate, final LocalDate endDate) {
        return generateReport(userId, startDate, endDate, PaymentScope.ALL);
    }

    public CashFlowReport generateReport(final Long userId,
                                         final LocalDate startDate,
                                         final LocalDate endDate,
                                         final PaymentScope scope) {
        validatePeriod(startDate, endDate);

        final Instant startAt = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        final Instant endAt = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        final List<CalendarEvent> events = eventRepo.findIdentifiedWithServiceLinksByUserAndPeriod(
                userId,
                startAt,
                endAt
        );
        final Map<Long, BigDecimal> paidByEvent = loadPaidByEvent(events, scope);
        final List<DailyEntry> entries = CashFlowDailyEntries.build(
                events,
                startDate,
                endDate,
                scope,
                paidByEvent,
                paidSvc
        );
        final RevenueReportService.SyncMetadata syncMeta = buildSyncMeta(userId);
        return new CashFlowReport(entries, startDate, endDate, syncMeta);
    }

    private Map<Long, BigDecimal> loadPaidByEvent(final List<CalendarEvent> events, final PaymentScope scope) {
        Map<Long, BigDecimal> paidByEvent = Map.of();
        if (scope == PaymentScope.PAID_ONLY) {
            paidByEvent = paidSvc.loadPaidAmountsByEventId(events.stream().map(CalendarEvent::getId).toList());
        }
        return paidByEvent;
    }

    private void validatePeriod(final LocalDate startDate, final LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new InvalidPeriodException("Start date must be before or equal to end date");
        }
        if (endDate.isAfter(startDate.plusMonths(MAX_MONTHS))) {
            throw new InvalidPeriodException("Period must not exceed " + MAX_MONTHS + " month");
        }
    }

    private RevenueReportService.SyncMetadata buildSyncMeta(final Long userId) {
        final Instant threshold = Instant.now().minus(freshMins, ChronoUnit.MINUTES);
        return syncRepo.findByUserId(userId)
                .map(state -> SyncStateReportMetadataFactory.create(state, threshold))
                .orElse(EMPTY_SYNC_META);
    }

    public record CashFlowReport(List<DailyEntry> entries, LocalDate startDate, LocalDate endDate,
                                 RevenueReportService.SyncMetadata syncMetadata) {}

    public record DailyEntry(LocalDate date, BigDecimal total, List<ServiceEntry> services) {}

    public record ServiceEntry(String name, int quantity, BigDecimal total) {}
}
