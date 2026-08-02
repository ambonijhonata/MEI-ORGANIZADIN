package com.api.calendar;

import com.api.common.PageRequestSanitizer;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class CalendarEventQueryService {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_FIELDS = Set.of(
            "id",
            "googleEventId",
            "title",
            "eventStart",
            "eventEnd",
            "identified",
            "serviceDescriptionSnapshot",
            "serviceValueSnapshot",
            "paymentType"
    );

    private final CalendarEventRepository eventRepository;
    private final CalendarEventPaymentRepository paymentRepository;
    private final SyncStateRepository stateRepository;

    public CalendarEventQueryService(
            final CalendarEventRepository eventRepository,
            final CalendarEventPaymentRepository paymentRepository,
            final SyncStateRepository stateRepository
    ) {
        this.eventRepository = eventRepository;
        this.paymentRepository = paymentRepository;
        this.stateRepository = stateRepository;
    }

    public Page<CalendarEventReadModel> listEvents(
            final Long userId,
            final LocalDate eventStart,
            final LocalDate eventEnd,
            final Pageable pageable
    ) {
        final Pageable sanitizedPageable = PageRequestSanitizer.sanitizePageable(
                pageable,
                SORT_FIELDS,
                DEFAULT_PAGE,
                DEFAULT_PAGE_SIZE,
                MAX_PAGE_SIZE
        );
        final Page<CalendarEvent> page = findEvents(userId, eventStart, eventEnd, sanitizedPageable);
        final Map<Long, BigDecimal> paidAmounts = loadPaidAmounts(page.getContent());
        return page.map(event -> CalendarEventReadModelMapper.toReadModel(new CalendarEventReadModelMapper.Input(
                event.getId(),
                event.getGoogleEventId(),
                event.getTitle(),
                event.getEventStartText(),
                event.getEventEndText(),
                event.isIdentified(),
                event.getServiceDescriptionSnapshot(),
                event.getServiceValueOrZero(),
                event.getPaymentTypeName(),
                paidAmounts.get(event.getId())
        )));
    }

    public CalendarIntegrationStatusReadModel getIntegrationStatus(final Long userId) {
        return stateRepository.findByUserId(userId)
                .map(CalendarIntegrationStatusMapper::toReadModel)
                .orElse(new CalendarIntegrationStatusReadModel(
                        SyncStatus.NEVER_SYNCED.name(), null, null, null
                ));
    }

    private Map<Long, BigDecimal> loadPaidAmounts(final List<CalendarEvent> events) {
        final Map<Long, BigDecimal> paidAmounts;
        if (events == null || events.isEmpty()) {
            paidAmounts = Map.of();
        } else {
            final List<Long> eventIds = events.stream()
                    .map(CalendarEvent::getId)
                    .toList();

            final Map<Long, BigDecimal> totals = new HashMap<>();
            for (final CalendarEventPaymentTotal total : paymentRepository.summarizePaidAmountsByEventIdIn(eventIds)) {
                totals.put(total.eventId(), total.paidAmount());
            }
            paidAmounts = totals;
        }
        return paidAmounts;
    }

    private Page<CalendarEvent> findEvents(
            final Long userId,
            final LocalDate eventStart,
            final LocalDate eventEnd,
            final Pageable pageable
    ) {
        final Page<CalendarEvent> page;
        if (eventStart != null && eventEnd != null) {
            final Instant start = eventStart.atStartOfDay(ZoneOffset.UTC).toInstant();
            final Instant end = eventEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            page = eventRepository.findByUserIdAndEventStartGreaterThanEqualAndEventStartLessThan(
                    userId, start, end, pageable
            );
        } else if (eventStart != null) {
            final Instant start = eventStart.atStartOfDay(ZoneOffset.UTC).toInstant();
            page = eventRepository.findByUserIdAndEventStartGreaterThanEqual(userId, start, pageable);
        } else if (eventEnd != null) {
            final Instant end = eventEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            page = eventRepository.findByUserIdAndEventStartLessThan(userId, end, pageable);
        } else {
            page = eventRepository.findByUserId(userId, pageable);
        }
        return page;
    }
}
