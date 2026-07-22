package com.api.google;

import com.google.api.client.util.DateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class GoogleCalendarPageReader<B> {

    private final Function<B, List<ItemView>> itemsFn;

    public GoogleCalendarPageReader(final Function<B, List<ItemView>> itemsFn) {
        this.itemsFn = itemsFn;
    }

    public List<GoogleCalendarSyncEvent> read(final B batch) {
        final List<ItemView> items = itemsFn.apply(batch);
        final List<GoogleCalendarSyncEvent> snapshots;
        if (items == null || items.isEmpty()) {
            snapshots = List.of();
        } else {
            final List<GoogleCalendarSyncEvent> mutableSnapshots = new ArrayList<>(items.size());
            for (final ItemView item : items) {
                if (item != null) {
                    mutableSnapshots.add(new GoogleCalendarSyncEvent(
                            item.eventId(),
                            item.summary(),
                            item.status(),
                            readInstant(item.start()),
                            readInstant(item.end())
                    ));
                }
            }
            snapshots = List.copyOf(mutableSnapshots);
        }
        return snapshots;
    }

    private Instant readInstant(final TimeView timeValue) {
        final DateTime dateTimeValue = timeValue == null ? null : timeValue.dateTime();
        final DateTime dateValue = timeValue == null ? null : timeValue.date();
        final Instant extractedInstant;
        if (dateTimeValue != null) {
            extractedInstant = Instant.ofEpochMilli(dateTimeValue.getValue());
        } else if (dateValue != null) {
            extractedInstant = Instant.ofEpochMilli(dateValue.getValue());
        } else {
            extractedInstant = Instant.now();
        }
        return extractedInstant;
    }

    public record ItemView(String eventId, String summary, String status, TimeView start, TimeView end) {
    }

    public record TimeView(DateTime dateTime, DateTime date) {
    }
}
