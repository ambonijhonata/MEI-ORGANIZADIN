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

    private final CalendarEventRepository calendarEventRepository;
    private final ReportPaidAmountService reportPaidAmountService;
    private final SyncStateRepository syncStateRepository;
    private final long freshnessMinutes;

    public CashFlowReportService(CalendarEventRepository calendarEventRepository,
                                   ReportPaidAmountService reportPaidAmountService,
                                   SyncStateRepository syncStateRepository,
                                   @Value("${sync.freshness-minutes}") long freshnessMinutes) {
        this.calendarEventRepository = calendarEventRepository;
        this.reportPaidAmountService = reportPaidAmountService;
        this.syncStateRepository = syncStateRepository;
        this.freshnessMinutes = freshnessMinutes;
    }

    public CashFlowReport generateReport(Long userId, LocalDate startDate, LocalDate endDate) {
        return generateReport(userId, startDate, endDate, PaymentScope.ALL);
    }

    public CashFlowReport generateReport(Long userId, LocalDate startDate, LocalDate endDate, PaymentScope paymentScope) {
        validatePeriod(startDate, endDate);

        Instant startInstant = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<CalendarEvent> events = calendarEventRepository.findIdentifiedWithServiceLinksByUserAndPeriod(
                userId,
                startInstant,
                endInstant
        );
        Map<Long, BigDecimal> paidAmountsByEventId = paymentScope == PaymentScope.PAID_ONLY
                ? reportPaidAmountService.loadPaidAmountsByEventId(events.stream().map(CalendarEvent::getId).toList())
                : Map.of();

        Map<LocalDate, Map<String, BigDecimal>> dailyServiceTotals = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dailyServiceTotals.put(date, new TreeMap<>());
        }

        for (CalendarEvent event : events) {
            LocalDate eventDate = event.getEventStart().atZone(ZoneOffset.UTC).toLocalDate();
            Map<String, BigDecimal> serviceMap = dailyServiceTotals.get(eventDate);
            if (serviceMap == null) {
                continue;
            }

            Map<String, BigDecimal> eventContributions = resolveEventServiceContributions(
                    event,
                    paymentScope,
                    paidAmountsByEventId
            );
            for (var contribution : eventContributions.entrySet()) {
                serviceMap.merge(contribution.getKey(), contribution.getValue(), BigDecimal::add);
            }
        }

        List<DailyEntry> entries = new ArrayList<>();
        for (var entry : dailyServiceTotals.entrySet()) {
            BigDecimal dayTotal = entry.getValue().values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            List<ServiceEntry> services = entry.getValue().entrySet().stream()
                    .map(e -> new ServiceEntry(e.getKey(), e.getValue()))
                    .sorted(
                            Comparator.comparing(ServiceEntry::total, Comparator.reverseOrder())
                                    .thenComparing(ServiceEntry::name)
                    )
                    .toList();

            entries.add(new DailyEntry(entry.getKey(), dayTotal, services));
        }

        RevenueReportService.SyncMetadata metadata = buildSyncMetadata(userId);

        return new CashFlowReport(entries, startDate, endDate, metadata);
    }

    private Map<String, BigDecimal> resolveEventServiceContributions(CalendarEvent event,
                                                                     PaymentScope paymentScope,
                                                                     Map<Long, BigDecimal> paidAmountsByEventId) {
        Map<String, BigDecimal> serviceValues = extractEventServiceValues(event);
        if (serviceValues.isEmpty()) {
            return Map.of();
        }

        if (paymentScope == PaymentScope.ALL) {
            return serviceValues;
        }

        BigDecimal eventServiceTotal = serviceValues.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidOnlyAmount = reportPaidAmountService.resolvePaidOnlyEventAmount(
                event,
                eventServiceTotal,
                paidAmountsByEventId
        );
        if (paidOnlyAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Map.of();
        }

        if (serviceValues.size() == 1) {
            String serviceName = serviceValues.keySet().iterator().next();
            return Map.of(serviceName, paidOnlyAmount);
        }

        List<String> serviceNames = new ArrayList<>(serviceValues.keySet());
        List<BigDecimal> weights = serviceNames.stream().map(serviceValues::get).toList();
        List<BigDecimal> distributedAmounts = reportPaidAmountService.distributeAmountProportionally(
                paidOnlyAmount,
                weights
        );

        Map<String, BigDecimal> distributedByService = new TreeMap<>();
        for (int i = 0; i < serviceNames.size(); i++) {
            distributedByService.put(serviceNames.get(i), distributedAmounts.get(i));
        }
        return distributedByService;
    }

    private Map<String, BigDecimal> extractEventServiceValues(CalendarEvent event) {
        Map<String, BigDecimal> serviceValues = new TreeMap<>();
        for (CalendarEventServiceLink serviceLink : event.getServiceLinks()) {
            String serviceName = serviceLink.getServiceDescriptionSnapshot();
            if (serviceName == null || serviceName.isBlank()) {
                continue;
            }
            serviceValues.merge(serviceName, safeAmount(serviceLink.getServiceValueSnapshot()), BigDecimal::add);
        }

        if (!serviceValues.isEmpty()) {
            return serviceValues;
        }

        String legacyServiceName = event.getServiceDescriptionSnapshot();
        if (legacyServiceName != null && !legacyServiceName.isBlank()) {
            serviceValues.put(legacyServiceName, safeAmount(event.getServiceValueSnapshot()));
        }
        return serviceValues;
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
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
