package com.api.calendar;

import com.api.google.GoogleCalendarClient;
import com.api.user.ApplicationUser;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CalendarSyncRequestExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(CalendarSyncRequestExecutor.class);
    private static final String MODE_FULL = "full_no_token";
    private static final String MODE_INC = "incremental";
    private static final String MODE_RESYNC = "full_resync_410";
    private static final String MODE_START = "start_date_sync";

    private final GoogleCalendarClient calendarClient;
    private final CalendarSyncFlowRunner flowRunner;

    /* package */ CalendarSyncRequestExecutor(final GoogleCalendarClient calendarClient,
                                              final CalendarSyncFlowRunner flowRunner) {
        this.calendarClient = calendarClient;
        this.flowRunner = flowRunner;
    }

    /* package */ CalendarSyncService.SyncResult runIncremental(final Long userId,
                                                                final ApplicationUser user,
                                                                final SyncState syncState) throws IOException {
        final String syncToken = syncState.operationalState().snapshot().syncToken();
        final long totalStartNs = System.nanoTime();
        final boolean fullSync = !hasToken(syncToken);
        final String mode = fullSync ? MODE_FULL : MODE_INC;
        CalendarSyncService.SyncResult result;

        try {
            result = executeRequest(
                    new SyncFetchRequest(userId, user, syncState, syncToken, syncToken, fullSync, true, mode),
                    totalStartNs,
                    false
            );
        } catch (GoogleCalendarClient.SyncTokenExpiredException ex) {
            logFallback(totalStartNs);
            result = runFullResync(userId, user, syncState);
        }
        return result;
    }

    /* package */ CalendarSyncService.SyncResult runFullResync(final Long userId,
                                                               final ApplicationUser user,
                                                               final SyncState syncState) throws IOException {
        final long totalStartNs = System.nanoTime();
        syncState.operationalState().clearSyncToken();
        return executeRequest(
                new SyncFetchRequest(
                        userId,
                        user,
                        syncState,
                        null,
                        syncState.operationalState().snapshot().syncToken(),
                        true,
                        false,
                        MODE_RESYNC
                ),
                totalStartNs,
                true
        );
    }

    /* package */ CalendarSyncService.SyncResult runStartDate(final Long userId,
                                                              final ApplicationUser user,
                                                              final SyncState syncState,
                                                              final LocalDate startDate) throws IOException {
        final String tokenBefore = syncState.operationalState().snapshot().syncToken();
        final long totalStartNs = System.nanoTime();
        return executeRequest(
                new SyncFetchRequest(userId, user, syncState, null, tokenBefore, false, false, MODE_START, startDate),
                totalStartNs,
                false
        );
    }

    /* package */ boolean hasToken(final String token) {
        return token != null && !token.isBlank();
    }

    private CalendarSyncService.SyncResult executeRequest(final SyncFetchRequest request,
                                                          final long totalStartNs,
                                                          final boolean fallback) throws IOException {
        final long fetchStartNs = System.nanoTime();
        final GoogleCalendarClient.CalendarSyncResult fetchResult = fetch(request);
        final long googleFetchMs = elapsedMs(fetchStartNs);
        final CalendarSyncExecution execution = flowRunner.run(new CalendarSyncExecutionRequest(
                request.userId(),
                request.user(),
                request.syncState(),
                fetchResult.events(),
                request.fullSync(),
                request.allowDeletes(),
                request.tokenBefore(),
                fetchResult.nextSyncToken(),
                request.mode(),
                request.startDate(),
                new HashMap<>()
        ));
        logSummary(request, fetchResult, execution, googleFetchMs, totalStartNs, fallback);
        return execution.result();
    }

    private GoogleCalendarClient.CalendarSyncResult fetch(final SyncFetchRequest request) throws IOException {
        return request.startDate() != null
                ? calendarClient.fetchEvents(request.userId(), null, request.startDate())
                : calendarClient.fetchEvents(request.userId(), request.fetchToken());
    }

    private void logSummary(final SyncFetchRequest request,
                            final GoogleCalendarClient.CalendarSyncResult fetchResult,
                            final CalendarSyncExecution execution,
                            final long googleFetchMs,
                            final long totalStartNs,
                            final boolean fallback) {
        flowRunner.logSummary(new CalendarSyncSummary(
                request.mode(),
                fetchResult.events() == null ? 0 : fetchResult.events().size(),
                execution.result().created(),
                execution.result().updated(),
                execution.result().deleted(),
                googleFetchMs,
                execution.dbLookupMs(),
                execution.processingMs(),
                execution.dbWriteMs(),
                elapsedMs(totalStartNs),
                fallback,
                hasToken(request.tokenBefore()),
                hasToken(request.syncState().operationalState().snapshot().syncToken())
        ));
    }

    private void logFallback(final long totalStartNs) {
        if (LOG.isInfoEnabled()) {
            LOG.info(
                    "calendar_sync_full_resync_fallback reason=sync_token_expired elapsed_ms={}",
                    elapsedMs(totalStartNs)
            );
        }
    }

    private long elapsedMs(final long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    private record SyncFetchRequest(
            Long userId,
            ApplicationUser user,
            SyncState syncState,
            String fetchToken,
            String tokenBefore,
            boolean fullSync,
            boolean allowDeletes,
            String mode,
            LocalDate startDate
    ) {
        private SyncFetchRequest(final Long userId,
                                 final ApplicationUser user,
                                 final SyncState syncState,
                                 final String fetchToken,
                                 final String tokenBefore,
                                 final boolean fullSync,
                                 final boolean allowDeletes,
                                 final String mode) {
            this(userId, user, syncState, fetchToken, tokenBefore, fullSync, allowDeletes, mode, null);
        }
    }
}
