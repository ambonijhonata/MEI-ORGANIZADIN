package com.api.google;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

final class GoogleCalendarRequestExecutor {

    private static final int DEF_PAGE_SIZE = 1000;
    private static final String DEF_FIELDS = "items(id,summary,status,start,end),nextPageToken,nextSyncToken";

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
        final GoogleCalendarGatewayRequest request =
                GoogleCalendarGatewayRequest.create(calendarService, pageSize, fields);
        final List<GoogleCalendarSyncEvent> allSnapshots = new ArrayList<>();

        applyPlan(plan, request);

        String nextPageToken;
        String nextSyncToken;
        do {
            final GoogleCalendarRequestPage page = request.fetchPage();
            allSnapshots.addAll(page.snapshots());
            nextPageToken = page.nextPageToken();
            nextSyncToken = page.nextSyncToken();
            request.setPageToken(nextPageToken);
        } while (nextPageToken != null);

        return new GoogleCalendarClient.CalendarSyncResult(allSnapshots, nextSyncToken);
    }

    private RequestPlan buildPlan(final String syncTok, final LocalDate startDate) {
        final String cleanSyncTok = syncTok == null || syncTok.isBlank() ? null : syncTok;
        final DateTime timeMin = startDate == null ? null : toDateTime(startDate);
        return new RequestPlan(cleanSyncTok, timeMin);
    }

    private DateTime toDateTime(final LocalDate startDate) {
        final Instant startUtc = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        return new DateTime(startUtc.toEpochMilli());
    }

    private void applyPlan(
            final RequestPlan plan,
            final GoogleCalendarGatewayRequest request) throws IOException {
        if (plan.hasSyncTok()) {
            request.applySyncToken(plan.syncTok());
            request.enableShowDeleted();
        } else if (plan.timeMin() != null) {
            request.applyTimeMin(plan.timeMin());
        }
    }

    private record RequestPlan(String syncTok, DateTime timeMin) {
        private boolean hasSyncTok() {
            return syncTok != null;
        }
    }
}
