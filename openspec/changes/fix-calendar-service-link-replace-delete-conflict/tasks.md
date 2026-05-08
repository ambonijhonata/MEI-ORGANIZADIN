## 1. Persistence Contract

- [x] 1.1 Refactor the persisted-event service replacement flow so it uses exactly one canonical removal strategy for `calendar_event_services`.
- [x] 1.2 Update the `CalendarEvent` aggregate and mapping rules to avoid scheduling implicit orphan deletes that conflict with repository-driven replacement.
- [x] 1.3 Keep `enrich` as an additive path distinct from `replace`, preserving valid existing snapshots while allowing explicit full replacement when required.

## 2. Sync And Reprocessing Integration

- [x] 2.1 Adjust `CalendarSyncService` to run persisted service-link replacement through the canonical delete-then-insert flow.
- [x] 2.2 Verify `CalendarEventReprocessor` uses the correct path for replacement versus enrichment and does not reintroduce double-delete behavior.
- [x] 2.3 Document the operational validation path for redeploying the API and retrying synchronization on the previously failing account.

## 3. Regression Coverage

- [x] 3.1 Add tests reproducing replacement of service links on an already persisted event without `duplicate key` failure.
- [x] 3.2 Add tests reproducing the follow-up synchronization failure path and asserting no `delete where id=?` stale-state error occurs.
- [x] 3.3 Add tests confirming an enriched appointment remains canonical and financially consistent after a later sync.

## 4. Verification

- [x] 4.1 Run the focused backend suite for calendar sync, reprocessing, and service-link persistence.
- [ ] 4.2 Execute a smoke test with the problematic account sequence and confirm synchronization completes after redeploy.
