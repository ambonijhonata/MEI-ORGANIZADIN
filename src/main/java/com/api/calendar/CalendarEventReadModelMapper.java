package com.api.calendar;

import java.math.BigDecimal;

final class CalendarEventReadModelMapper {
    private CalendarEventReadModelMapper() {
    }

    public static CalendarEventReadModel toReadModel(final Input input) {
        return new CalendarEventReadModel(
                input.entityId(),
                input.googleEventId(),
                input.title(),
                input.eventStart(),
                input.eventEnd(),
                input.identified(),
                input.serviceDesc(),
                input.serviceValue(),
                input.paymentType(),
                input.paidAmount()
        );
    }

    public record Input(
            Long entityId,
            String googleEventId,
            String title,
            String eventStart,
            String eventEnd,
            boolean identified,
            String serviceDesc,
            BigDecimal serviceValue,
            String paymentType,
            BigDecimal paidAmount
    ) {
    }
}
