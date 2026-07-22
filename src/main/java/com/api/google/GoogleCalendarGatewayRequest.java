package com.api.google;

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Events;
import java.io.IOException;

final class GoogleCalendarGatewayRequest {

    private static final String CAL_ID = "primary";

    private final RequestOps request;

    private GoogleCalendarGatewayRequest(final RequestOps request) {
        this.request = request;
    }

    public static GoogleCalendarGatewayRequest create(
            final Calendar calendarService,
            final int pageSize,
            final String fields) throws IOException {
        return new GoogleCalendarGatewayRequest(
                new GoogleCalendarSdkRequest<>(
                        calendarService.events()
                                .list(CAL_ID)
                                .setSingleEvents(true)
                                .setMaxResults(pageSize)
                                .setFields(fields),
                        AbstractGoogleClientRequest::execute,
                        Calendar.Events.List::setPageToken,
                        Calendar.Events.List::setSyncToken,
                        GoogleCalendarRequestMutations::enableShowDeleted,
                        Calendar.Events.List::setTimeMin,
                        Events::getNextPageToken,
                        Events::getNextSyncToken,
                        GoogleCalendarPageReaders.googleEvents()
                )
        );
    }

    public GoogleCalendarRequestPage fetchPage() throws IOException {
        return request.fetchPage();
    }

    public void setPageToken(final String pageToken) throws IOException {
        request.setPageToken(pageToken);
    }

    public void applySyncToken(final String syncTok) throws IOException {
        request.applySyncToken(syncTok);
    }

    public void enableShowDeleted() throws IOException {
        request.enableShowDeleted();
    }

    public void applyTimeMin(final DateTime timeMin) throws IOException {
        request.applyTimeMin(timeMin);
    }

    private interface RequestOps {
        GoogleCalendarRequestPage fetchPage() throws IOException;

        void setPageToken(String pageToken) throws IOException;

        void applySyncToken(String syncTok) throws IOException;

        void enableShowDeleted() throws IOException;

        void applyTimeMin(DateTime timeMin) throws IOException;
    }

    private static final class GoogleCalendarSdkRequest<R, B> implements RequestOps {

        private final R request;
        private final GoogleIoFunction<R, B> execFn;
        private final GoogleIoSetter<R, String> pageTokFn;
        private final GoogleIoSetter<R, String> syncTokFn;
        private final GoogleIoMutator<R> showDeletedFn;
        private final GoogleIoSetter<R, DateTime> timeMinFn;
        private final java.util.function.Function<B, String> pageTokReader;
        private final java.util.function.Function<B, String> syncTokReader;
        private final GoogleCalendarPageReader<? super B> pageReader;

        private GoogleCalendarSdkRequest(
                final R request,
                final GoogleIoFunction<R, B> execFn,
                final GoogleIoSetter<R, String> pageTokFn,
                final GoogleIoSetter<R, String> syncTokFn,
                final GoogleIoMutator<R> showDeletedFn,
                final GoogleIoSetter<R, DateTime> timeMinFn,
                final java.util.function.Function<B, String> pageTokReader,
                final java.util.function.Function<B, String> syncTokReader,
                final GoogleCalendarPageReader<? super B> pageReader) {
            this.request = request;
            this.execFn = execFn;
            this.pageTokFn = pageTokFn;
            this.syncTokFn = syncTokFn;
            this.showDeletedFn = showDeletedFn;
            this.timeMinFn = timeMinFn;
            this.pageTokReader = pageTokReader;
            this.syncTokReader = syncTokReader;
            this.pageReader = pageReader;
        }

        @Override
        public GoogleCalendarRequestPage fetchPage() throws IOException {
            final B batch = execFn.apply(request);
            return new GoogleCalendarRequestPage(
                    pageReader.read(batch),
                    pageTokReader.apply(batch),
                    syncTokReader.apply(batch)
            );
        }

        @Override
        public void setPageToken(final String pageToken) throws IOException {
            pageTokFn.accept(request, pageToken);
        }

        @Override
        public void applySyncToken(final String syncTok) throws IOException {
            syncTokFn.accept(request, syncTok);
        }

        @Override
        public void enableShowDeleted() throws IOException {
            showDeletedFn.accept(request);
        }

        @Override
        public void applyTimeMin(final DateTime timeMin) throws IOException {
            timeMinFn.accept(request, timeMin);
        }
    }

    @FunctionalInterface
    private interface GoogleIoFunction<I, O> {
        O apply(I input) throws IOException;
    }

    @FunctionalInterface
    private interface GoogleIoSetter<I, V> {
        void accept(I input, V value) throws IOException;
    }

    @FunctionalInterface
    private interface GoogleIoMutator<I> {
        void accept(I input) throws IOException;
    }
}
