package com.api.google;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("PMD.LooseCoupling")
final class GoogleCalendarRequestExecutor {

    private static final int DEF_PAGE_SIZE = 1000;
    private static final String DEF_FIELDS = "items(id,summary,status,start,end),nextPageToken,nextSyncToken";
    private static final String CAL_ID = "primary";

    private final String fields;
    private final int pageSize;

    public GoogleCalendarRequestExecutor(final int pageSize, final String requestFields) {
        this.pageSize = pageSize > 0 ? pageSize : DEF_PAGE_SIZE;
        this.fields = requestFields == null || requestFields.isBlank() ? DEF_FIELDS : requestFields;
    }

    public GoogleCalendarClient.CalendarSyncResult fetch(
            final Calendar calendarService,
            final String syncTok,
            final LocalDate startDate) throws IOException {
        final RequestPlan plan = buildPlan(syncTok, startDate);
        final Calendar.Events.List request = createRequest(calendarService, plan);
        final List<Event> allEvents = new ArrayList<>();

        Events batch;
        do {
            batch = request.execute();
            allEvents.addAll(readItems(batch));
            request.setPageToken(batch.getNextPageToken());
        } while (batch.getNextPageToken() != null);

        return new GoogleCalendarClient.CalendarSyncResult(mapEvents(allEvents), batch.getNextSyncToken());
    }

    private RequestPlan buildPlan(final String syncTok, final LocalDate startDate) {
        final String cleanSyncTok = syncTok == null || syncTok.isBlank() ? null : syncTok;
        return new RequestPlan(cleanSyncTok, buildTimeMin(startDate));
    }

    private DateTime buildTimeMin(final LocalDate startDate) {
        final DateTime timeMin;
        if (startDate == null) {
            timeMin = null;
        } else {
            final Instant startUtc = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
            timeMin = new DateTime(startUtc.toEpochMilli());
        }
        return timeMin;
    }

    private Calendar.Events.List createRequest(
            final Calendar calendarService,
            final RequestPlan plan) throws IOException {
        final Calendar.Events.List request = calendarService.events()
                .list(CAL_ID)
                .setSingleEvents(true)
                .setMaxResults(pageSize)
                .setFields(fields);

        applySyncToken(request, plan);
        applyTimeMin(request, plan);
        return request;
    }

    private void applySyncToken(final Calendar.Events.List request, final RequestPlan plan) throws IOException {
        if (plan.hasSyncTok()) {
            request.setSyncToken(plan.syncTok());
            request.setShowDeleted(true);
        }
    }

    private void applyTimeMin(final Calendar.Events.List request, final RequestPlan plan) throws IOException {
        if (!plan.hasSyncTok() && plan.timeMin() != null) {
            request.setTimeMin(plan.timeMin());
        }
    }

    private List<Event> readItems(final Events batch) {
        final List<Event> items = batch.getItems();
        return items == null ? Collections.emptyList() : items;
    }

    private List<GoogleCalendarSyncEvent> mapEvents(final List<Event> googleEvents) {
        final List<GoogleCalendarSyncEvent> snapshots;
        if (googleEvents == null || googleEvents.isEmpty()) {
            snapshots = List.of();
        } else {
            final List<GoogleCalendarSyncEvent> mappedSnapshots = new ArrayList<>(googleEvents.size());
            for (final Event googleEvent : googleEvents) {
                if (googleEvent != null) {
                    mappedSnapshots.add(new GoogleCalendarSyncEvent(
                            googleEvent.getId(),
                            googleEvent.getSummary(),
                            googleEvent.getStatus(),
                            extractInstant(googleEvent.getStart()),
                            extractInstant(googleEvent.getEnd())
                    ));
                }
            }
            snapshots = List.copyOf(mappedSnapshots);
        }
        return snapshots;
    }

    @SuppressWarnings("PMD.LawOfDemeter")
    private Instant extractInstant(final EventDateTime eventDateTime) {
        Instant extractedInstant = Instant.now();
        if (eventDateTime != null) {
            if (eventDateTime.getDateTime() != null) {
                extractedInstant = Instant.ofEpochMilli(eventDateTime.getDateTime().getValue());
            } else if (eventDateTime.getDate() != null) {
                extractedInstant = Instant.ofEpochMilli(eventDateTime.getDate().getValue());
            }
        }
        return extractedInstant;
    }

    private record RequestPlan(String syncTok, DateTime timeMin) {
        private boolean hasSyncTok() {
            return syncTok != null;
        }
    }
}
