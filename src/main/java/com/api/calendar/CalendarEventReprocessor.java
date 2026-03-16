package com.api.calendar;

import com.api.servicecatalog.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class CalendarEventReprocessor {

    private final CalendarEventRepository calendarEventRepository;
    private final CalendarEventServiceMatcher matcher;

    public CalendarEventReprocessor(CalendarEventRepository calendarEventRepository,
                                     CalendarEventServiceMatcher matcher) {
        this.calendarEventRepository = calendarEventRepository;
        this.matcher = matcher;
    }

    @Async
    @Transactional
    public void reprocessUnidentifiedEvents(Long userId) {
        List<CalendarEvent> unidentified = calendarEventRepository.findByUserIdAndIdentifiedFalse(userId);

        for (CalendarEvent event : unidentified) {
            Optional<Service> matchedService = matcher.matchService(userId, event.getTitle());
            matchedService.ifPresent(event::associateService);
        }

        calendarEventRepository.saveAll(unidentified);
    }
}
