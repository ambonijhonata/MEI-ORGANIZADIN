package com.api.calendar;

import com.api.common.GoogleApiAccessDeniedException;
import com.api.common.IntegrationRevokedException;
import com.api.google.GoogleCalendarClient;
import com.api.user.ApplicationUser;
import com.api.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDate;

@Component
public class CalendarSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(CalendarSyncService.class);
    private static final String USER_NOT_FOUND = "ApplicationUser not found";
    private static final String REAUTH_REQUIRED = "Google integration requires re-authentication";
    private static final String API_FORBIDDEN = "GOOGLE_API_FORBIDDEN";
    private static final String IO_ERROR = "IO_ERROR";
    private static final String INTERNAL_ERROR = "INTERNAL_SYNC_ERROR";

    private final GoogleCalendarClient calendarClient;
    private final SyncStateRepository syncStateRepo;
    private final UserRepository userRepo;
    private final UserScopedExecutionLock execLock;
    private final CalendarSyncFlowRunner flowRunner;
    private final CalendarSyncRequestExecutor requestExecutor;

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
        this.requestExecutor = new CalendarSyncRequestExecutor(calendarClient, flowRunner);
    }

    public SyncResult synchronize(final Long userId) {
        return synchronize(userId, null);
    }

    public SyncResult synchronize(final Long userId, final LocalDate startDate) {
        return execLock.execute(userId, () -> synchronizeUser(userId, startDate));
    }

    private SyncResult synchronizeUser(final Long userId, final LocalDate startDate) {
        final ApplicationUser user = userRepo.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException(USER_NOT_FOUND));
        final SyncState syncState = syncStateRepo.findByUserId(userId)
                .orElseGet(() -> syncStateRepo.save(new SyncState(user)));

        if (syncState.operationalState().isReauthRequired()) {
            throw new IntegrationRevokedException(REAUTH_REQUIRED);
        }

        syncState.operationalState().markSyncing();
        syncStateRepo.save(syncState);

        return runSyncOperation(userId, user, syncState, startDate);
    }

    private SyncResult runSyncOperation(final Long userId,
                                        final ApplicationUser user,
                                        final SyncState syncState,
                                        final LocalDate startDate) {
        boolean completed = false;
        try {
            final SyncResult result = executeSyncPath(userId, user, syncState, startDate);
            completed = true;
            return result;
        } catch (GoogleCalendarClient.OAuthRevokedException oauthEx) {
            throw handleOauthRevocation(syncState, oauthEx);
        } catch (GoogleCalendarClient.GoogleApiForbiddenException forbiddenEx) {
            throw handleForbidden(syncState, forbiddenEx);
        } catch (IOException ioEx) {
            throw handleIoFailure(syncState, ioEx);
        } finally {
            finalizeIncompleteSync(syncState, completed);
        }
    }

    private SyncResult executeSyncPath(final Long userId,
                                       final ApplicationUser user,
                                       final SyncState syncState,
                                       final LocalDate startDate) throws IOException {
        final String syncToken = syncState.operationalState().snapshot().syncToken();
        final boolean startDateSync = startDate != null && !requestExecutor.hasToken(syncToken);
        return startDateSync
                ? requestExecutor.runStartDate(userId, user, syncState, startDate)
                : requestExecutor.runIncremental(userId, user, syncState);
    }

    private IntegrationRevokedException handleOauthRevocation(final SyncState syncState,
                                                              final GoogleCalendarClient.OAuthRevokedException oauthEx) {
        syncState.operationalState().markReauthRequired(oauthEx.getMessage());
        syncStateRepo.save(syncState);
        return new IntegrationRevokedException(oauthEx.getMessage(), oauthEx);
    }

    private GoogleApiAccessDeniedException handleForbidden(final SyncState syncState,
                                                           final GoogleCalendarClient.GoogleApiForbiddenException forbiddenEx) {
        syncState.operationalState().markFailed(API_FORBIDDEN, forbiddenEx.getMessage());
        syncStateRepo.save(syncState);
        return new GoogleApiAccessDeniedException(forbiddenEx.getMessage(), forbiddenEx);
    }

    private IllegalStateException handleIoFailure(final SyncState syncState, final IOException ioEx) {
        syncState.operationalState().markFailed(IO_ERROR, ioEx.getMessage());
        syncStateRepo.save(syncState);
        return new IllegalStateException("Sync failed: " + ioEx.getMessage(), ioEx);
    }

    private void finalizeIncompleteSync(final SyncState syncState, final boolean completed) {
        if (!completed && syncState.operationalState().isSyncing()) {
            syncState.operationalState().markFailed(
                    INTERNAL_ERROR,
                    "Unexpected internal error during calendar synchronization"
            );
            syncStateRepo.save(syncState);
        }
    }

    public record SyncResult(int created, int updated, int deleted) {}
}
