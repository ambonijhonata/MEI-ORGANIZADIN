## 1. Persistence Ordering

- [x] 1.1 Refactor `CalendarSyncService.persistMutations` so persisted-event service-link replacement runs before deletion cleanups that can flush the persistence context.
- [x] 1.2 Ensure the replace flow for `calendar_event_services` still performs explicit delete-and-flush before saving updated events.
- [x] 1.3 Review reconciliation and deletion paths in the same method to confirm the new ordering does not skip event or payment cleanup.

## 2. Autoflush Hardening

- [x] 2.1 Adjust the payment cleanup repository/service interaction so it no longer triggers an autoflush before the service-link replacement phase.
- [x] 2.2 Audit other sync-time cleanup queries for hidden flush behavior that could reintroduce the same duplicate-key window.

## 3. Regression Coverage

- [x] 3.1 Add a sync test that reproduces a single chunk containing both a deleted event and a persisted event that replaces recognized services.
- [x] 3.2 Assert that the mixed-mutation sync path completes without violating `ux_calendar_event_services_event_service`.
- [x] 3.3 Add or update tests to confirm canonical service links remain stable after a follow-up sync on the affected flow.

## 4. Validation

- [x] 4.1 Run the focused backend test suite for calendar sync, cleanup ordering, and service-link persistence.
- [ ] 4.2 Execute the production-oriented smoke test sequence against the problematic account after deploy and confirm sync completes without `INTERNAL_SERVER_ERROR`.
