package com.api.calendar;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CalendarEventLookupRepository {

    Page<CalendarEvent> findByUserId(Long userId, Pageable pageable);

    Optional<CalendarEvent> findByIdAndUserId(Long eventId, Long userId);
}
