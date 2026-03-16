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

@Component
public class RevenueReportService {

    private final CalendarEventServiceLinkRepository serviceLinkRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final SyncStateRepository syncStateRepository;
    private final long freshnessMinutes;

    public RevenueReportService(CalendarEventServiceLinkRepository serviceLinkRepository,
                                 CalendarEventRepository calendarEventRepository,
                                 SyncStateRepository syncStateRepository,
                                 @Value("${sync.freshness-minutes}") long freshnessMinutes) {
        this.serviceLinkRepository = serviceLinkRepository;
        this.calendarEventRepository = calendarEventRepository;
        this.syncStateRepository = syncStateRepository;
        this.freshnessMinutes = freshnessMinutes;
    }

    public RevenueReport generateReport(Long userId, LocalDate startDate, LocalDate endDate) {
        validatePeriod(startDate, endDate, 12);

        Instant startInstant = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<CalendarEventServiceLink> links = serviceLinkRepository.findByUserAndPeriod(userId, startInstant, endInstant);

        BigDecimal totalRevenue = links.stream()
                .map(CalendarEventServiceLink::getServiceValueSnapshot)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Fallback: also include legacy single-service events without links
        BigDecimal legacyRevenue = calendarEventRepository.sumRevenueByUserAndPeriod(userId, startInstant, endInstant);
        if (links.isEmpty() && legacyRevenue.compareTo(BigDecimal.ZERO) > 0) {
            totalRevenue = legacyRevenue;
        }

        SyncMetadata metadata = buildSyncMetadata(userId);

        return new RevenueReport(totalRevenue, startDate, endDate, metadata);
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

    private SyncMetadata buildSyncMetadata(Long userId) {
        return syncStateRepository.findByUserId(userId)
                .map(state -> {
                    boolean dataUpToDate = isDataUpToDate(state);
                    boolean reauthRequired = state.getStatus() == SyncStatus.REAUTH_REQUIRED;
                    return new SyncMetadata(dataUpToDate, state.getLastSyncAt(), reauthRequired);
                })
                .orElse(new SyncMetadata(false, null, false));
    }

    private boolean isDataUpToDate(SyncState state) {
        if (state.getLastSyncAt() == null) return false;
        Instant threshold = Instant.now().minus(freshnessMinutes, ChronoUnit.MINUTES);
        return state.getLastSyncAt().isAfter(threshold);
    }

    public record RevenueReport(BigDecimal totalRevenue, LocalDate startDate, LocalDate endDate,
                                  SyncMetadata syncMetadata) {}

    public record SyncMetadata(boolean dataUpToDate, Instant lastSyncAt, boolean reauthRequired) {}
}
