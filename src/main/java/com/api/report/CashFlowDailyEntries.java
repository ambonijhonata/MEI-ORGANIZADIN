package com.api.report;

import com.api.calendar.CalendarEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class CashFlowDailyEntries {

    private CashFlowDailyEntries() {
    }

    /* default */ static List<CashFlowReportService.DailyEntry> build(final List<CalendarEvent> events,
                                                                      final LocalDate startDate,
                                                                      final LocalDate endDate,
                                                                      final PaymentScope scope,
                                                                      final Map<Long, BigDecimal> paidByEvent,
                                                                      final ReportPaidAmountService paidSvc) {
        return new Builder(scope, paidByEvent, paidSvc).build(events, startDate, endDate);
    }

    private static final class Builder {
        private static final int ONE = 1;

        private final PaymentScope scope;
        private final Map<Long, BigDecimal> paidByEvent;
        private final ReportPaidAmountService paidSvc;

        private Builder(final PaymentScope scope,
                        final Map<Long, BigDecimal> paidByEvent,
                        final ReportPaidAmountService paidSvc) {
            this.scope = scope;
            this.paidByEvent = paidByEvent;
            this.paidSvc = paidSvc;
        }

        private List<CashFlowReportService.DailyEntry> build(final List<CalendarEvent> events,
                                                             final LocalDate startDate,
                                                             final LocalDate endDate) {
            final Map<LocalDate, Map<String, Totals>> dayMap = Stream
                    .iterate(startDate, day -> !day.isAfter(endDate), day -> day.plusDays(ONE))
                    .collect(Collectors.toMap(
                            day -> day,
                            day -> new TreeMap<>(),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));

            for (final CalendarEvent event : events) {
                final Map<String, Totals> svcMap = dayMap.get(event.getEventDateUtc());
                if (svcMap != null) {
                    serviceVals(event).forEach((name, contrib) -> svcMap.merge(name, Totals.from(contrib), Totals::merge));
                }
            }

            return dayMap.entrySet().stream()
                    .map(this::toDayEntry)
                    .toList();
        }

        private Map<String, Contrib> serviceVals(final CalendarEvent event) {
            final Map<String, Contrib> svcVals = event.getServiceLinks().stream()
                    .filter(link -> link.getServiceDescriptionSnapshot() != null && !link.getServiceDescriptionSnapshot().isBlank())
                    .collect(Collectors.toMap(
                            link -> link.getServiceDescriptionSnapshot(),
                            link -> new Contrib(link.getServiceValueSnapshot() != null ? link.getServiceValueSnapshot() : BigDecimal.ZERO, ONE),
                            (left, right) -> new Contrib(left.total().add(right.total()), left.quantity() + right.quantity()),
                            TreeMap::new
                    ));

            if (svcVals.isEmpty()) {
                final String legacyName = event.getServiceDescriptionSnapshot();
                if (legacyName != null && !legacyName.isBlank()) {
                    svcVals.put(
                            legacyName,
                            new Contrib(event.getServiceValueOrZero(), ONE)
                    );
                }
            }

            Map<String, Contrib> resolvedVals = svcVals;
            if (scope != PaymentScope.ALL && !svcVals.isEmpty()) {
                final BigDecimal svcTotal = svcVals.values().stream()
                        .map(Contrib::total)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                final BigDecimal paidAmt = paidSvc.resolvePaidOnlyEventAmount(event, svcTotal, paidByEvent);
                if (paidAmt.compareTo(BigDecimal.ZERO) <= 0) {
                    resolvedVals = Map.of();
                } else if (svcVals.size() == ONE) {
                    final String svcName = svcVals.keySet().iterator().next();
                    resolvedVals = Map.of(svcName, new Contrib(paidAmt, svcVals.get(svcName).quantity()));
                } else {
                    final List<String> svcNames = List.copyOf(svcVals.keySet());
                    final List<BigDecimal> paidParts = paidSvc.distributeAmountProportionally(
                            paidAmt,
                            svcNames.stream().map(name -> svcVals.get(name).total()).toList()
                    );
                    resolvedVals = Stream.iterate(0, index -> index < svcNames.size(), index -> index + 1)
                            .collect(Collectors.toMap(
                                    index -> svcNames.get(index),
                                    index -> new Contrib(paidParts.get(index), svcVals.get(svcNames.get(index)).quantity()),
                                    (left, right) -> left,
                                    TreeMap::new
                            ));
                }
            }
            return resolvedVals;
        }

        private CashFlowReportService.DailyEntry toDayEntry(final Map.Entry<LocalDate, Map<String, Totals>> dayEntry) {
            final Map<String, Totals> svcMap = dayEntry.getValue();
            final BigDecimal dayTotal = svcMap.values().stream()
                    .map(Totals::total)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            final List<CashFlowReportService.ServiceEntry> svcEntries = svcMap.entrySet().stream()
                    .map(entry -> new CashFlowReportService.ServiceEntry(
                            entry.getKey(),
                            entry.getValue().quantity(),
                            entry.getValue().total()
                    ))
                    .sorted(
                            Comparator.comparing(CashFlowReportService.ServiceEntry::total, Comparator.reverseOrder())
                                    .thenComparing(CashFlowReportService.ServiceEntry::name)
                    )
                    .toList();
            return new CashFlowReportService.DailyEntry(dayEntry.getKey(), dayTotal, svcEntries);
        }
    }

    private record Contrib(BigDecimal total, int quantity) {
    }

    private record Totals(BigDecimal total, int quantity) {
        private static Totals from(final Contrib contrib) {
            return new Totals(contrib.total(), contrib.quantity());
        }

        private Totals merge(final Totals other) {
            return new Totals(total.add(other.total()), quantity + other.quantity());
        }
    }
}
