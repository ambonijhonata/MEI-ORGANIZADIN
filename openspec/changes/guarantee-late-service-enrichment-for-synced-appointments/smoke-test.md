## Smoke Test Status

- Date: 2026-05-08
- Scope: `userId=4` with agendamentos containing `sobrancelha`, `buco`, `tintura`, and `henna`
- Status: pending manual execution after deploy

## What Was Verified Locally

- Focused backend suite passed:
  - `mvn clean "-Dtest=SyncStateTest,ServiceCatalogServiceTest,CalendarEventReprocessorTest,CalendarSyncServiceTest,CalendarSyncServiceExtendedTest,CalendarSyncServiceConcurrencyTest,CalendarSyncServiceLoadTest,CashFlowReportServiceExtendedTest" test`
- Regression coverage now includes:
  - legacy partially identified appointments completed by a later sync without Google delta
  - recovery when the async enrichment fast path did not run
  - no full Google resync required to enrich already synchronized appointments

## Manual Production Smoke To Run

1. Deploy the API with this change and Flyway migration `V13__persist_catalog_enrichment_revision_for_sync_state.sql`.
2. Log in with the affected account for `userId=4`.
3. Trigger synchronization once.
4. Confirm that:
   - `Michela - Sobrancelha` stays with `sobrancelha`
   - `Sofia - sobrancelha + buco` becomes `sobrancelha + buco`
   - `Talita - sobrancelha + tintura` becomes `sobrancelha + tintura`
   - `Jhonata - sobrancelha + henna` stays `sobrancelha + henna`
5. Confirm no full Google resync was needed and no sync error was raised.
