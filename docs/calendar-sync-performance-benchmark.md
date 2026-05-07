# Calendar Sync Performance Benchmark

This runbook defines how to execute and compare baseline and optimized sync performance for `POST /api/calendar/sync`.

## Goal

Validate performance gains for high-volume accounts (target scenario around 14k+ events) without functional regressions.

## Load Scenario

- Dataset: approximately `14,354` Google events for one user.
- Mix:
- Confirmed events with realistic title patterns (`{client} - {service}` and multi-service variants).
- Optional cancelled events for incremental delete path validation.
- Trigger pattern:
- Repeated `POST /api/calendar/sync` calls in incremental mode.
- Forced token-expired run to validate full resync fallback path.

## Validation Environment (Low Resource)

- Runtime budget:
- Memory limit: `512MB`
- CPU quota: `0.1` vCPU equivalent
- Baseline command example (Docker):
```bash
docker run --rm \
  --memory=512m \
  --cpus=0.1 \
  -e DB_URL=... \
  -e DB_USERNAME=... \
  -e DB_PASSWORD=... \
  -e GOOGLE_OAUTH_CLIENT_ID=... \
  -e GOOGLE_OAUTH_CLIENT_SECRET=... \
  -e GOOGLE_CALENDAR_SYNC_MAX_RESULTS=2500 \
  -e CALENDAR_SYNC_BATCH_SIZE=1000 \
  -e CALENDAR_SYNC_BATCH_CLEAR_ENABLED=true \
  -e CALENDAR_SYNC_BATCH_FLUSH_EVERY_CHUNKS=1 \
  -e POSTGRES_REWRITE_BATCHED_INSERTS=true \
  <api-image>
```
- Mandatory report fields for each run:
- memory limit and cpu limit used
- commit SHA
- dataset identifier (reference account with 14,482 events)
- p50/p95/p99 for `sync_total_ms`

## Instrumentation to Collect

- `sync_total_ms`
- `google_fetch_ms`
- `db_lookup_ms`
- `processing_ms`
- `db_write_ms`
- `events_received`
- `created`, `updated`, `deleted`
- full resync fallback flag

## Suggested Execution Steps

1. Run on baseline branch/commit and collect metrics.
2. Run on optimized branch/commit with same dataset and environment.
3. Compare p50/p95/p99 and stage breakdown.
4. Validate contract invariants using regression tests.
5. Record bottleneck stage ranking (`google_fetch_ms`, `db_lookup_ms`, `processing_ms`, `db_write_ms`) for each profile.

## Result Template

| Build | Dataset | p50 sync_total_ms | p95 sync_total_ms | p99 sync_total_ms | google_fetch_ms p95 | db_lookup_ms p95 | db_write_ms p95 | Notes |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| Baseline | 14,354 |  |  |  |  |  |  |  |
| Optimized | 14,354 |  |  |  |  |  |  |  |

## Manual Harness

- Unit harness (manual): `CalendarSyncServiceLoadTest` (disabled by default).
- For production-like evaluation, prefer running against real API + PostgreSQL + real Google test account in staging.

## Current Validation Status

- Automated regression validation completed for sync and security contracts.
- Real benchmark with production-like Google account (`14,482` events) must be executed in staging before closing the 10-second target.
- If p95 remains above 10 seconds after this fix, prioritize optimization by highest stage contribution in this order: `google_fetch_ms`, `db_lookup_ms`, `processing_ms`, `db_write_ms`.
