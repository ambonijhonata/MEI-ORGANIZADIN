package com.api.google;

import java.util.List;

record GoogleCalendarRequestPage(
        List<GoogleCalendarSyncEvent> snapshots,
        String nextPageToken,
        String nextSyncToken) {

    GoogleCalendarRequestPage {
        snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
    }
}
