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

## Result Template

| Build | Dataset | p50 sync_total_ms | p95 sync_total_ms | p99 sync_total_ms | google_fetch_ms p95 | db_lookup_ms p95 | db_write_ms p95 | Notes |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| Baseline | 14,354 |  |  |  |  |  |  |  |
| Optimized | 14,354 |  |  |  |  |  |  |  |

## Manual Harness

- Unit harness (manual): `CalendarSyncServiceLoadTest` (disabled by default).
- For production-like evaluation, prefer running against real API + PostgreSQL + real Google test account in staging.
