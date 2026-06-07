package com.api.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEventTiming {
    @Column(name = "event_start", nullable = false)
    private Instant start;

    @Column(name = "event_end")
    private Instant end;

    private CalendarEventTiming(final Instant start, final Instant end) {
        this.start = start;
        this.end = end;
    }

    public static CalendarEventTiming empty() {
        return new CalendarEventTiming(null, null);
    }

    public static CalendarEventTiming create(final Instant start, final Instant end) {
        return new CalendarEventTiming(start, end);
    }

    public void update(final Instant start, final Instant end) {
        this.start = start;
        this.end = end;
    }

    public String startText() {
        return start != null ? start.toString() : null;
    }

    public String endText() {
        return end != null ? end.toString() : null;
    }
}
