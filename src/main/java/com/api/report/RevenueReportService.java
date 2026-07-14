package com.api.report;

import com.api.calendar.CalendarEvent;
import com.api.calendar.CalendarEventRepository;
import com.api.calendar.SyncState;
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
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RevenueReportService {

    private static final int MAX_PERIOD_MONTHS = 12;

    private final CalendarEventRepository eventRepo;
    private final ReportPaidAmountService paidAmtSvc;
    private final SyncStateRepository stateRepo;
    private final long syncFreshnessMins;

    public RevenueReportService(final CalendarEventRepository eventRepo,
                                final ReportPaidAmountService paidAmtSvc,
                                final SyncStateRepository stateRepo,
                                @Value("${sync.freshness-minutes}") final long syncFreshnessMins) {
        this.eventRepo = eventRepo;
        this.paidAmtSvc = paidAmtSvc;
        this.stateRepo = stateRepo;
        this.syncFreshnessMins = syncFreshnessMins;
    }

    public RevenueReport generateReport(final Long userId, final LocalDate startDate, final LocalDate endDate) {
        return generateReport(userId, startDate, endDate, PaymentScope.ALL);
    }

    public RevenueReport generateReport(final Long userId,
                                        final LocalDate startDate,
                                        final LocalDate endDate,
                                        final PaymentScope paymentScope) {
        validatePeriod(startDate, endDate, MAX_PERIOD_MONTHS);

        final Instant startInstant = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        final Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        final List<CalendarEvent> events = eventRepo.findIdentifiedByUserAndPeriod(userId, startInstant, endInstant);
        final BigDecimal totalRevenue = resolveTotalRevenue(events, paymentScope);
        final SyncMetadata metadata = buildSyncMetadata(userId);

        return new RevenueReport(totalRevenue, startDate, endDate, metadata);
    }

    private BigDecimal resolveTotalRevenue(final List<CalendarEvent> events, final PaymentScope paymentScope) {
        BigDecimal totalRevenue = sumAllRevenue(events);
        if (paymentScope == PaymentScope.PAID_ONLY) {
            totalRevenue = sumPaidOnlyRevenue(events);
        }
        return totalRevenue;
    }

    private BigDecimal sumAllRevenue(final List<CalendarEvent> events) {
        return events.stream()
                .map(this::eventServiceAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumPaidOnlyRevenue(final List<CalendarEvent> events) {
        final List<Long> eventIds = events.stream().map(CalendarEvent::getId).toList();
        final Map<Long, BigDecimal> paidByEvent = paidAmtSvc.loadPaidAmountsByEventId(eventIds);
        return events.stream()
                .map(event -> paidAmtSvc.resolvePaidOnlyEventAmount(event, eventServiceAmount(event), paidByEvent))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal eventServiceAmount(final CalendarEvent event) {
        return safeAmount(event.getServiceValueSnapshot());
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
        final Instant threshold = Instant.now().minus(syncFreshnessMins, ChronoUnit.MINUTES);
        final Optional<SyncState> stateOpt = stateRepo.findByUserId(userId);
        SyncMetadata metadata = new SyncMetadata(false, null, false);
        if (stateOpt.isPresent()) {
            metadata = SyncStateReportMetadataFactory.create(stateOpt.get(), threshold);
        }
        return metadata;
    }

    public record RevenueReport(BigDecimal totalRevenue, LocalDate startDate, LocalDate endDate,
                                  SyncMetadata syncMetadata) {}

    public record SyncMetadata(boolean dataUpToDate, Instant lastSyncAt, boolean reauthRequired) {}
}
