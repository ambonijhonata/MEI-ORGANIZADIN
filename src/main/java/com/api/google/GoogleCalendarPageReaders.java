package com.api.google;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

final class GoogleCalendarPageReaders {

    private GoogleCalendarPageReaders() {
    }

    public static GoogleCalendarPageReader<Object> googleEvents() {
        return new GoogleCalendarPageReader<>(GoogleCalendarPageReaders::readItems);
    }

    private static List<GoogleCalendarPageReader.ItemView> readItems(final Object rawBatch) {
        final List<?> items = ((Events) rawBatch).getItems();
        final List<GoogleCalendarPageReader.ItemView> itemViews;
        if (items == null || items.isEmpty()) {
            itemViews = List.of();
        } else {
            itemViews = items.stream()
                    .filter(java.util.Objects::nonNull)
                    .map(GoogleCalendarPageReaders::toItemView)
                    .toList();
        }
        return itemViews;
    }

    private static GoogleCalendarPageReader.ItemView toItemView(final Object rawEvent) {
        return new GoogleCalendarPageReader.ItemView(
                ((Event) rawEvent).getId(),
                ((Event) rawEvent).getSummary(),
                ((Event) rawEvent).getStatus(),
                toTimeView(((Event) rawEvent).getStart()),
                toTimeView(((Event) rawEvent).getEnd())
        );
    }

    private static GoogleCalendarPageReader.TimeView toTimeView(final Object rawValue) {
        return new GoogleCalendarPageReader.TimeView(
                readDateTime(rawValue, "getDateTime"),
                readDateTime(rawValue, "getDate")
        );
    }

    private static DateTime readDateTime(final Object rawValue, final String methodName) {
        final DateTime result;
        if (rawValue == null) {
            result = null;
        } else {
            try {
                result = (DateTime) EventDateTime.class.getMethod(methodName).invoke(rawValue);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException exception) {
                throw new IllegalStateException("Unable to read Google calendar date field", exception);
            }
        }
        return result;
    }
}
