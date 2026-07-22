package com.api.calendar;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CalendarEventServiceSnapshot {
    @Column(name = "service_description_snapshot", length = 500)
    private String description;

    @Column(name = "service_value_snapshot", precision = 12, scale = 2)
    private BigDecimal totalValue;

    private CalendarEventServiceSnapshot(final String description, final BigDecimal totalValue) {
        this.description = description;
        this.totalValue = totalValue;
    }

    public static CalendarEventServiceSnapshot empty() {
        return new CalendarEventServiceSnapshot(null, null);
    }

    public static CalendarEventServiceSnapshot create(final String description, final BigDecimal totalValue) {
        return new CalendarEventServiceSnapshot(description, totalValue);
    }

    public void update(final String description, final BigDecimal totalValue) {
        this.description = description;
        this.totalValue = totalValue;
    }

    public boolean hasDescription() {
        return description != null && !description.isBlank();
    }

    public boolean hasValue() {
        return totalValue != null;
    }

    public BigDecimal totalValueOrZero() {
        return totalValue != null ? totalValue : BigDecimal.ZERO;
    }

    public String descriptionOr(final String fallbackDesc) {
        return hasDescription() ? description : fallbackDesc;
    }

    public BigDecimal totalValueOr(final BigDecimal fallbackTotal) {
        return hasValue() ? totalValue : fallbackTotal;
    }
}
