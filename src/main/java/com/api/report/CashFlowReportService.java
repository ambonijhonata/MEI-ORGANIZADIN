package com.api.report;

import com.api.calendar.*;
import com.api.common.InvalidPeriodException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class CashFlowReportService {

    private final CalendarEventServiceLinkRepository serviceLinkRepository;
    private final SyncStateRepository syncStateRepository;
    private final long freshnessMinutes;

    public CashFlowReportService(CalendarEventServiceLinkRepository serviceLinkRepository,
                                   SyncStateRepository syncStateRepository,
                                   @Value("${sync.freshness-minutes}") long freshnessMinutes) {
        this.serviceLinkRepository = serviceLinkRepository;
        this.syncStateRepository = syncStateRepository;
        this.freshnessMinutes = freshnessMinutes;
    }

    public CashFlowReport generateReport(Long userId, LocalDate startDate, LocalDate endDate) {
        validatePeriod(startDate, endDate);

        Instant startInstant = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<CalendarEventServiceLink> links = serviceLinkRepository.findByUserAndPeriod(
                userId, startInstant, endInstant);

        // Group by date -> service name -> sum
        Map<LocalDate, Map<String, BigDecimal>> dailyServiceTotals = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dailyServiceTotals.put(date, new TreeMap<>());
        }

        for (CalendarEventServiceLink link : links) {
            LocalDate eventDate = link.getCalendarEvent().getEventStart().atZone(ZoneOffset.UTC).toLocalDate();
            Map<String, BigDecimal> serviceMap = dailyServiceTotals.get(eventDate);
            if (serviceMap != null) {
                serviceMap.merge(link.getServiceDescriptionSnapshot(), link.getServiceValueSnapshot(), BigDecimal::add);
            }
        }

        List<DailyEntry> entries = new ArrayList<>();
        for (var entry : dailyServiceTotals.entrySet()) {
            BigDecimal dayTotal = entry.getValue().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<ServiceEntry> services = entry.getValue().entrySet().stream()
                    .map(e -> new ServiceEntry(e.getKey(), e.getValue()))
                    .toList();

            entries.add(new DailyEntry(entry.getKey(), dayTotal, services));
        }

        RevenueReportService.SyncMetadata metadata = buildSyncMetadata(userId);

        return new CashFlowReport(entries, startDate, endDate, metadata);
    }

    private void validatePeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new InvalidPeriodException("Start date must be before or equal to end date");
        }
        long daysBetween = ChronoUnit.DAYS.between(startDate, endDate);
        if (daysBetween > 7) {
            throw new InvalidPeriodException("Period must not exceed 7 days");
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
        if (state.getLastSyncAt() == null) return false;
        Instant threshold = Instant.now().minus(freshnessMinutes, ChronoUnit.MINUTES);
        return state.getLastSyncAt().isAfter(threshold);
    }

    public record CashFlowReport(List<DailyEntry> entries, LocalDate startDate, LocalDate endDate,
                                    RevenueReportService.SyncMetadata syncMetadata) {}

    public record DailyEntry(LocalDate date, BigDecimal total, List<ServiceEntry> services) {}

    public record ServiceEntry(String name, BigDecimal total) {}
}
