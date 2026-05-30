package com.api.report;

import com.api.calendar.*;
import com.api.common.InvalidPeriodException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@SuppressWarnings({"PMD.AvoidInstantiatingObjectsInLoops", "PMD.AvoidLiteralsInIfCondition", "PMD.ControlStatementBraces", "PMD.CouplingBetweenObjects", "PMD.LawOfDemeter", "PMD.LongVariable", "PMD.OnlyOneReturn", "PMD.UseExplicitTypes"})
@Component
public class CashFlowReportService {

    private final CalendarEventRepository calendarEventRepository;
    private final ReportPaidAmountService reportPaidAmountService;
    private final SyncStateRepository syncStateRepository;
    private final long freshnessMinutes;

    public CashFlowReportService(final CalendarEventRepository calendarEventRepository,
                                   final ReportPaidAmountService reportPaidAmountService,
                                   final SyncStateRepository syncStateRepository,
                                   @Value("${sync.freshness-minutes}") final long freshnessMinutes) {
        this.calendarEventRepository = calendarEventRepository;
        this.reportPaidAmountService = reportPaidAmountService;
        this.syncStateRepository = syncStateRepository;
        this.freshnessMinutes = freshnessMinutes;
    }

    public CashFlowReport generateReport(final Long userId, final LocalDate startDate, final LocalDate endDate) {
        return generateReport(userId, startDate, endDate, PaymentScope.ALL);
    }

    public CashFlowReport generateReport(final Long userId, final LocalDate startDate, final LocalDate endDate, final PaymentScope paymentScope) {
        validatePeriod(startDate, endDate);

        final Instant startInstant = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        final Instant endInstant = endDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        final List<CalendarEvent> events = calendarEventRepository.findIdentifiedWithServiceLinksByUserAndPeriod(
                userId,
                startInstant,
                endInstant
        );
        final Map<Long, BigDecimal> paidAmountsByEventId = paymentScope == PaymentScope.PAID_ONLY
                ? reportPaidAmountService.loadPaidAmountsByEventId(events.stream().map(CalendarEvent::getId).toList())
                : Map.of();

        final Map<LocalDate, Map<String, ServiceAggregate>> dailyServiceTotals = new LinkedHashMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dailyServiceTotals.put(date, new TreeMap<>());
        }

        for (final CalendarEvent event : events) {
            final LocalDate eventDate = event.getEventStart().atZone(ZoneOffset.UTC).toLocalDate();
            final Map<String, ServiceAggregate> serviceMap = dailyServiceTotals.get(eventDate);
            if (serviceMap == null) {
                continue;
            }

            final Map<String, ServiceContribution> eventContributions = resolveEventServiceContributions(
                    event,
                    paymentScope,
                    paidAmountsByEventId
            );
            for (final var contribution : eventContributions.entrySet()) {
                serviceMap.merge(
                        contribution.getKey(),
                        ServiceAggregate.from(contribution.getValue()),
                        ServiceAggregate::merge
                );
            }
        }

        final List<DailyEntry> entries = new ArrayList<>();
        for (final var entry : dailyServiceTotals.entrySet()) {
            final BigDecimal dayTotal = entry.getValue().values().stream()
                    .map(ServiceAggregate::total)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            final List<ServiceEntry> services = entry.getValue().entrySet().stream()
                    .map(e -> new ServiceEntry(e.getKey(), e.getValue().quantity(), e.getValue().total()))
                    .sorted(
                            Comparator.comparing(ServiceEntry::total, Comparator.reverseOrder())
                                    .thenComparing(ServiceEntry::name)
                    )
                    .toList();

            entries.add(new DailyEntry(entry.getKey(), dayTotal, services));
        }

        final RevenueReportService.SyncMetadata metadata = buildSyncMetadata(userId);

        return new CashFlowReport(entries, startDate, endDate, metadata);
    }

    private Map<String, ServiceContribution> resolveEventServiceContributions(final CalendarEvent event,
                                                                              final PaymentScope paymentScope,
                                                                              final Map<Long, BigDecimal> paidAmountsByEventId) {
        final Map<String, ServiceContribution> serviceValues = extractEventServiceValues(event);
        if (serviceValues.isEmpty()) {
            return Map.of();
        }

        if (paymentScope == PaymentScope.ALL) {
            return serviceValues;
        }

        final BigDecimal eventServiceTotal = serviceValues.values().stream()
                .map(ServiceContribution::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal paidOnlyAmount = reportPaidAmountService.resolvePaidOnlyEventAmount(
                event,
                eventServiceTotal,
                paidAmountsByEventId
        );
        if (paidOnlyAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Map.of();
        }

        if (serviceValues.size() == 1) {
            final String serviceName = serviceValues.keySet().iterator().next();
            return Map.of(serviceName, new ServiceContribution(paidOnlyAmount, serviceValues.get(serviceName).quantity()));
        }

        final List<String> serviceNames = new ArrayList<>(serviceValues.keySet());
        final List<BigDecimal> weights = serviceNames.stream().map(name -> serviceValues.get(name).total()).toList();
        final List<BigDecimal> distributedAmounts = reportPaidAmountService.distributeAmountProportionally(
                paidOnlyAmount,
                weights
        );

        final Map<String, ServiceContribution> distributedByService = new TreeMap<>();
        for (int i = 0; i < serviceNames.size(); i++) {
            final String serviceName = serviceNames.get(i);
            distributedByService.put(
                    serviceName,
                    new ServiceContribution(distributedAmounts.get(i), serviceValues.get(serviceName).quantity())
            );
        }
        return distributedByService;
    }

    private Map<String, ServiceContribution> extractEventServiceValues(final CalendarEvent event) {
        final Map<String, ServiceContribution> serviceValues = new TreeMap<>();
        for (final CalendarEventServiceLink serviceLink : event.getServiceLinks()) {
            final String serviceName = serviceLink.getServiceDescriptionSnapshot();
            if (serviceName == null || serviceName.isBlank()) {
                continue;
            }
            serviceValues.merge(
                    serviceName,
                    new ServiceContribution(safeAmount(serviceLink.getServiceValueSnapshot()), 1),
                    (left, right) -> new ServiceContribution(left.total().add(right.total()), left.quantity() + right.quantity())
            );
        }

        if (!serviceValues.isEmpty()) {
            return serviceValues;
        }

        final String legacyServiceName = event.getServiceDescriptionSnapshot();
        if (legacyServiceName != null && !legacyServiceName.isBlank()) {
            serviceValues.put(legacyServiceName, new ServiceContribution(safeAmount(event.getServiceValueSnapshot()), 1));
        }
        return serviceValues;
    }

    private BigDecimal safeAmount(final BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private void validatePeriod(final LocalDate startDate, final LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new InvalidPeriodException("Start date must be before or equal to end date");
        }
        if (endDate.isAfter(startDate.plusMonths(1))) {
            throw new InvalidPeriodException("Period must not exceed 1 month");
        }
    }

    private RevenueReportService.SyncMetadata buildSyncMetadata(final Long userId) {
        final Optional<SyncState> currentState = syncStateRepository.findByUserId(userId);
        if (currentState.isEmpty()) {
            return new RevenueReportService.SyncMetadata(false, null, false);
        }
        final SyncState state = currentState.get();
        final boolean dataUpToDate = isDataUpToDate(state);
        final boolean reauthRequired = state.getStatus() == SyncStatus.REAUTH_REQUIRED;
        return new RevenueReportService.SyncMetadata(dataUpToDate, state.getLastSyncAt(), reauthRequired);
    }

    private boolean isDataUpToDate(final SyncState state) {
        if (state.getLastSyncAt() == null) return false;
        final Instant threshold = Instant.now().minus(freshnessMinutes, ChronoUnit.MINUTES);
        return state.getLastSyncAt().isAfter(threshold);
    }

    public record CashFlowReport(List<DailyEntry> entries, LocalDate startDate, LocalDate endDate,
                                    RevenueReportService.SyncMetadata syncMetadata) {}

    public record DailyEntry(LocalDate date, BigDecimal total, List<ServiceEntry> services) {}

    public record ServiceEntry(String name, int quantity, BigDecimal total) {}

    private record ServiceContribution(BigDecimal total, int quantity) {}

    private record ServiceAggregate(BigDecimal total, int quantity) {
        private static ServiceAggregate from(final ServiceContribution contribution) {
            return new ServiceAggregate(contribution.total(), contribution.quantity());
        }

        private ServiceAggregate merge(final ServiceAggregate other) {
            return new ServiceAggregate(
                    total.add(other.total()),
                    quantity + other.quantity()
            );
        }
    }
}
