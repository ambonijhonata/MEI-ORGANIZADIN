package com.api.calendar;

import com.api.servicecatalog.Service;
import java.util.List;

record CalendarEventServiceState(
        Service primaryService,
        CalendarEventServiceSnapshot snapshot,
        List<CalendarEventServiceLink> serviceLinks,
        boolean identified,
        boolean changed
) {
    public static CalendarEventServiceState empty() {
        return new CalendarEventServiceState(
                null,
                CalendarEventServiceSnapshot.empty(),
                List.of(),
                false,
                true
        );
    }
}
