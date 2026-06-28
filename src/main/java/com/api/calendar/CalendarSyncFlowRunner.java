package com.api.calendar;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@SuppressWarnings("PMD.LooseCoupling")
public class CalendarSyncFlowRunner {

    private static final Logger LOG = LoggerFactory.getLogger(CalendarSyncFlowRunner.class);

    private final SyncStateRepository syncStateRepo;
    private final CalendarEventReprocessor reprocessor;
    private final CalendarSyncMutationPlanner planner;
    private final CalendarSyncScopeReconciler reconciler;
    private final CalendarSyncPersistenceSupport persistence;
    private final CalendarSyncBatchSettings batch;
    private final CalendarSyncTxSupport txSupport;

    public CalendarSyncFlowRunner(final SyncStateRepository syncStateRepo,
                                  final CalendarEventReprocessor reprocessor,
                                  final CalendarSyncMutationPlanner planner,
                                  final CalendarSyncScopeReconciler reconciler,
                                  final CalendarSyncPersistenceSupport persistence,
                                  final CalendarSyncBatchSettings batch,
                                  final CalendarSyncTxSupport txSupport) {
        this.syncStateRepo = syncStateRepo;
        this.reprocessor = reprocessor;
        this.planner = planner;
        this.reconciler = reconciler;
        this.persistence = persistence;
        this.batch = batch;
        this.txSupport = txSupport;
    }

    public CalendarSyncExecution run(final CalendarSyncExecutionRequest request) {
        final long lookupStart = System.nanoTime();
        final CalendarSyncLookups lookups = planner.buildLookups(request.userId());
        final long lookupMs = elapsedMs(lookupStart);

        final long processStart = System.nanoTime();
        final List<CalendarSyncMutations> chunkResults = processChunks(request, lookups);
        final CalendarSyncMutations merged = summarize(chunkResults);
        final CalendarScopeReconciliationResult scope = reconciler.reconcile(
                request.userId(),
                request.googleEvents(),
                merged,
                request.fullSync(),
                request.syncMode(),
                request.startDate()
        );
        final long processMs = elapsedMs(processStart);

        final long writeStart = System.nanoTime();
        txSupport.run(() -> persistAndFinalize(request, scope));
        final long writeMs = elapsedMs(writeStart);

        return new CalendarSyncExecution(
                new CalendarSyncService.SyncResult(
                        scope.reconciledMutations().created(),
                        scope.reconciledMutations().updated(),
                        scope.reconciledMutations().deleted()
                ),
                lookupMs,
                processMs,
                writeMs
        );
    }

    public void logSummary(final CalendarSyncSummary summary) {
        if (LOG.isInfoEnabled()) {
            LOG.info(
                    "calendar_sync_summary mode={} events_received={} created={} updated={} deleted={} google_fetch_ms={} db_lookup_ms={} processing_ms={} db_write_ms={} sync_total_ms={} fallback_from_expired_token={} token_before_present={} token_after_present={}",
                    summary.mode(),
                    summary.eventsReceived(),
                    summary.created(),
                    summary.updated(),
                    summary.deleted(),
                    summary.googleFetchMs(),
                    summary.dbLookupMs(),
                    summary.processingMs(),
                    summary.dbWriteMs(),
                    summary.totalMs(),
                    summary.fallback(),
                    summary.hadTokenBefore(),
                    summary.hasTokenAfter()
            );
        }
    }

    private void persistAndFinalize(final CalendarSyncExecutionRequest request,
                                    final CalendarScopeReconciliationResult scope) {
        if (!scope.additionalDeletions().isEmpty()) {
            persistence.persistMutations(new CalendarSyncMutations(
                    List.of(),
                    scope.additionalDeletions(),
                    Set.of(),
                    0,
                    0,
                    scope.additionalDeletions().size()
            ));
        }
        if (reprocessor != null && request.syncState().hasPendingCatalogEnrichment()) {
            reprocessor.enrichPendingSynchronizedAppointments(request.userId(), request.syncState());
        }
        updateSyncState(request.syncState(), request.tokenBeforeSync(), request.nextSyncToken(), request.syncMode());
        syncStateRepo.save(request.syncState());
    }

    private List<CalendarSyncMutations> processChunks(final CalendarSyncExecutionRequest request,
                                                      final CalendarSyncLookups lookups) {
        final List<CalendarSyncMutations> chunks = new ArrayList<>();
        final List<com.google.api.services.calendar.model.Event> googleEvents = request.googleEvents();
        if (googleEvents != null && !googleEvents.isEmpty()) {
            final int chunkSize = Math.max(1, batch.batchSize());
            for (int start = 0; start < googleEvents.size(); start += chunkSize) {
                chunks.add(processChunk(request, lookups, googleEvents, start, chunkSize));
            }
        }
        return chunks;
    }

    private CalendarSyncMutations processChunk(final CalendarSyncExecutionRequest request,
                                               final CalendarSyncLookups lookups,
                                               final List<com.google.api.services.calendar.model.Event> googleEvents,
                                               final int start,
                                               final int chunkSize) {
        final int end = Math.min(start + chunkSize, googleEvents.size());
        final CalendarSyncChunkRequest chunkRequest = new CalendarSyncChunkRequest(
                request.userId(),
                request.user(),
                googleEvents.subList(start, end),
                lookups,
                request.fullSync(),
                request.allowDeletes(),
                request.normCache()
        );
        return txSupport.get(() -> {
            final CalendarSyncMutations mutations = planner.processChunk(chunkRequest);
            persistence.persistMutations(mutations);
            return mutations;
        });
    }

    private CalendarSyncMutations summarize(final List<CalendarSyncMutations> chunks) {
        int created = 0;
        int updated = 0;
        int deleted = 0;
        final List<CalendarEvent> deletions = new ArrayList<>();
        if (chunks != null) {
            for (final CalendarSyncMutations chunk : chunks) {
                if (chunk != null) {
                    created += chunk.created();
                    updated += chunk.updated();
                    deleted += chunk.deleted();
                    deletions.addAll(chunk.deletions());
                }
            }
        }
        return new CalendarSyncMutations(List.of(), deletions, Set.of(), created, updated, deleted);
    }

    private void updateSyncState(final SyncState syncState,
                                 final String tokenBefore,
                                 final String nextToken,
                                 final String mode) {
        if (hasToken(nextToken)) {
            syncState.markSynced(nextToken);
        } else if (hasToken(tokenBefore)) {
            syncState.setLastSyncAt(Instant.now());
            syncState.setStatus(SyncStatus.SYNCED);
            syncState.setErrorCategory(null);
            syncState.setErrorMessage(null);
            syncState.setSyncToken(tokenBefore);
            LOG.warn("calendar_sync_token_missing mode={} action=preserve_existing_token token_before_present=true",
                    mode);
        } else {
            syncState.markSynced(null);
            LOG.warn("calendar_sync_token_missing mode={} action=keep_token_empty token_before_present=false", mode);
        }
    }

    private boolean hasToken(final String token) {
        return token != null && !token.isBlank();
    }

    private long elapsedMs(final long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
