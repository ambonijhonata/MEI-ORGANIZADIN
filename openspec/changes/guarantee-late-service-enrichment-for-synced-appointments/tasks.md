## 1. Persistent Retry State

- [x] 1.1 Add persisted per-user state for requested versus applied late-service enrichment so retries survive async failure, restart, and legacy data.
- [x] 1.2 Add a migration or bootstrap backfill that marks users with Google-backed synchronized appointments for at least one enrichment pass after deploy.

## 2. Catalog-Change Triggers

- [x] 2.1 Update `ServiceCatalogService` to persist the enrichment pending state on service creation and description-changing updates before firing the async fast path.
- [x] 2.2 Keep value-only service updates out of the Google repair path and out of unnecessary enrichment retries.

## 3. Sync Consumption

- [x] 3.1 Update `CalendarSyncService` to detect pending late-service enrichment and execute local reenrichment during sync even when Google returns no changed events.
- [x] 3.2 Clear the persisted pending state only after a successful full enrichment pass for that user.
- [x] 3.3 Ensure async enrichment and sync-driven enrichment share the same per-user lock and completion rules.

## 4. Regression Coverage

- [x] 4.1 Add tests for legacy partially identified appointments that become complete only after a post-deploy retry or manual sync.
- [x] 4.2 Add tests for failed or skipped async enrichment being recovered by a later synchronization.
- [x] 4.3 Add tests confirming no full Google resync is required to repair previously synchronized appointments with late-created services.

## 5. Verification

- [x] 5.1 Run the focused backend suite for service catalog, calendar sync, reprocessing, and financial report stability.
- [ ] 5.2 Execute a smoke scenario for `userId=4` covering `sobrancelha`, `buco`, `tintura`, and `henna`, and confirm agendamentos 2 and 3 become complete after the pending-enrichment retry path.
