package com.api.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEventLabel {
    @Column(length = 1000)
    private String title;

    @Column(name = "normalized_title", length = 1000)
    private String normalizedTitle;

    private CalendarEventLabel(final String title, final String normalizedTitle) {
        this.title = title;
        this.normalizedTitle = normalizedTitle;
    }

    public static CalendarEventLabel empty() {
        return new CalendarEventLabel(null, null);
    }

    public static CalendarEventLabel create(final String title, final String normalizedTitle) {
        return new CalendarEventLabel(title, normalizedTitle);
    }

    public void update(final String title, final String normalizedTitle) {
        this.title = title;
        this.normalizedTitle = normalizedTitle;
    }
}
