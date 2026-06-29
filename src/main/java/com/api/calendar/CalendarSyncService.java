package com.api.calendar;

import com.api.common.GoogleApiAccessDeniedException;
import com.api.common.IntegrationRevokedException;
import com.api.google.GoogleCalendarClient;
import com.api.user.User;
import com.api.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;

@Component
public class CalendarSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(CalendarSyncService.class);
    private static final String USER_NOT_FOUND = "User not found";
    private static final String REAUTH_REQUIRED = "Google integration requires re-authentication";
    private static final String API_FORBIDDEN = "GOOGLE_API_FORBIDDEN";
    private static final String IO_ERROR = "IO_ERROR";
    private static final String INTERNAL_ERROR = "INTERNAL_SYNC_ERROR";

    private final GoogleCalendarClient calendarClient;
    private final SyncStateRepository syncStateRepo;
    private final UserRepository userRepo;
    private final UserScopedExecutionLock execLock;
    private final CalendarSyncFlowRunner flowRunner;

    @Autowired
    public CalendarSyncService(final GoogleCalendarClient calendarClient,
                               final SyncStateRepository syncStateRepo,
                               final UserRepository userRepo,
                               final UserScopedExecutionLock execLock,
                               final CalendarSyncFlowRunner flowRunner) {
        this.calendarClient = calendarClient;
        this.syncStateRepo = syncStateRepo;
        this.userRepo = userRepo;
        this.execLock = execLock;
        this.flowRunner = flowRunner;
    }

    public SyncResult synchronize(final Long userId) {
        return synchronize(userId, null);
    }

    public SyncResult synchronize(final Long userId, final LocalDate startDate) {
        return execLock.execute(userId, () -> synchronizeUser(userId, startDate));
    }

    private SyncResult synchronizeUser(final Long userId, final LocalDate startDate) {
        final User user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND));
        final SyncState syncState = syncStateRepo.findByUserId(userId)
                .orElseGet(() -> syncStateRepo.save(new SyncState(user)));

        if (syncState.isReauthRequired()) {
            throw new IntegrationRevokedException(REAUTH_REQUIRED);
        }

        syncState.markSyncing();
        syncStateRepo.save(syncState);

        return runSyncOperation(userId, user, syncState, startDate);
    }

    @SuppressWarnings("PMD.CyclomaticComplexity")
    private SyncResult runSyncOperation(final Long userId,
                                        final User user,
                                        final SyncState syncState,
                                        final LocalDate startDate) {
        boolean completed = false;
        try {
            final SyncResult result = startDate != null && !hasToken(syncState.snapshot().syncToken())
                    ? performStartDateSync(userId, user, syncState, startDate)
                    : performSync(userId, user, syncState);
            completed = true;
            return result;
        } catch (GoogleCalendarClient.OAuthRevokedException oauthException) {
            syncState.markReauthRequired(oauthException.getMessage());
            syncStateRepo.save(syncState);
            throw new IntegrationRevokedException(oauthException.getMessage(), oauthException);
        } catch (GoogleCalendarClient.GoogleApiForbiddenException forbiddenEx) {
            syncState.markFailed(API_FORBIDDEN, forbiddenEx.getMessage());
            syncStateRepo.save(syncState);
            throw new GoogleApiAccessDeniedException(forbiddenEx.getMessage(), forbiddenEx);
        } catch (IOException ioException) {
            syncState.markFailed(IO_ERROR, ioException.getMessage());
            syncStateRepo.save(syncState);
            throw new IllegalStateException("Sync failed: " + ioException.getMessage(), ioException);
        } finally {
            if (!completed && syncState.snapshot().status() == SyncStatus.SYNCING) {
                syncState.markFailed(INTERNAL_ERROR, "Unexpected internal error during calendar synchronization");
                syncStateRepo.save(syncState);
            }
        }
    }

    private SyncResult performSync(final Long userId, final User user, final SyncState syncState) throws IOException {
        final String syncToken = syncState.snapshot().syncToken();
        final long totalStartNs = System.nanoTime();
        final boolean fullSync = !hasToken(syncToken);
        final String syncMode = fullSync ? "full_no_token" : "incremental";
        SyncResult result;

        try {
            final long fetchStartNs = System.nanoTime();
            final GoogleCalendarClient.CalendarSyncResult fetchResult =
                    calendarClient.fetchEvents(userId, syncToken);
            final long googleFetchMs = elapsedMs(fetchStartNs);
            final CalendarSyncExecution execution = flowRunner.run(new CalendarSyncExecutionRequest(
                    userId,
                    user,
                    syncState,
                    fetchResult.events(),
                    fullSync,
                    true,
                    syncToken,
                    fetchResult.nextSyncToken(),
                    syncMode,
                    null,
                    new HashMap<>()
            ));
            flowRunner.logSummary(new CalendarSyncSummary(
                    syncMode,
                    fetchResult.events() == null ? 0 : fetchResult.events().size(),
                    execution.result().created(),
                    execution.result().updated(),
                    execution.result().deleted(),
                    googleFetchMs,
                    execution.dbLookupMs(),
                    execution.processingMs(),
                    execution.dbWriteMs(),
                    elapsedMs(totalStartNs),
                    false,
                    hasToken(syncToken),
                    hasToken(syncState.snapshot().syncToken())
            ));
            result = execution.result();
        } catch (GoogleCalendarClient.SyncTokenExpiredException exception) {
            if (LOG.isInfoEnabled()) {
                LOG.info("calendar_sync_full_resync_fallback reason=sync_token_expired elapsed_ms={}",
                        elapsedMs(totalStartNs));
            }
            result = performFullResync(userId, user, syncState);
        }
        return result;
    }

    private SyncResult performFullResync(final Long userId, final User user, final SyncState syncState)
            throws IOException {
        final String tokenBeforeSync = syncState.snapshot().syncToken();
        final long totalStartNs = System.nanoTime();
        syncState.clearSyncToken();

        final long fetchStartNs = System.nanoTime();
        final GoogleCalendarClient.CalendarSyncResult result = calendarClient.fetchEvents(userId, null);
        final long googleFetchMs = elapsedMs(fetchStartNs);
        final CalendarSyncExecution execution = flowRunner.run(new CalendarSyncExecutionRequest(
                userId,
                user,
                syncState,
                result.events(),
                true,
                false,
                syncState.snapshot().syncToken(),
                result.nextSyncToken(),
                "full_resync_410",
                null,
                new HashMap<>()
        ));

        flowRunner.logSummary(new CalendarSyncSummary(
                "full_resync_410",
                result.events() == null ? 0 : result.events().size(),
                execution.result().created(),
                execution.result().updated(),
                execution.result().deleted(),
                googleFetchMs,
                execution.dbLookupMs(),
                execution.processingMs(),
                execution.dbWriteMs(),
                elapsedMs(totalStartNs),
                true,
                hasToken(tokenBeforeSync),
                hasToken(syncState.snapshot().syncToken())
        ));
        return execution.result();
    }

    private SyncResult performStartDateSync(final Long userId,
                                            final User user,
                                            final SyncState syncState,
                                            final LocalDate startDate) throws IOException {
        final String tokenBeforeSync = syncState.snapshot().syncToken();
        final long totalStartNs = System.nanoTime();
        final long fetchStartNs = System.nanoTime();
        final GoogleCalendarClient.CalendarSyncResult result =
                calendarClient.fetchEvents(userId, null, startDate);
        final long googleFetchMs = elapsedMs(fetchStartNs);
        final CalendarSyncExecution execution = flowRunner.run(new CalendarSyncExecutionRequest(
                userId,
                user,
                syncState,
                result.events(),
                false,
                false,
                tokenBeforeSync,
                result.nextSyncToken(),
                "start_date_sync",
                startDate,
                new HashMap<>()
        ));

        flowRunner.logSummary(new CalendarSyncSummary(
                "start_date_sync",
                result.events() == null ? 0 : result.events().size(),
                execution.result().created(),
                execution.result().updated(),
                execution.result().deleted(),
                googleFetchMs,
                execution.dbLookupMs(),
                execution.processingMs(),
                execution.dbWriteMs(),
                elapsedMs(totalStartNs),
                false,
                hasToken(tokenBeforeSync),
                hasToken(syncState.snapshot().syncToken())
        ));
        return execution.result();
    }

    private boolean hasToken(final String token) {
        return token != null && !token.isBlank();
    }

    private long elapsedMs(final long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }

    public record SyncResult(int created, int updated, int deleted) {}
}
