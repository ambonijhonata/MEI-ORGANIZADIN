package com.api.google;

import com.google.api.services.calendar.Calendar;

final class GoogleCalendarRequestMutations {

    private GoogleCalendarRequestMutations() {
    }

    public static void enableShowDeleted(final Object rawRequest) {
        ((Calendar.Events.List) rawRequest).setShowDeleted(true);
    }
}
