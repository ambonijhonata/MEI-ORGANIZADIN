## 1. Endpoint Contract Update

- [x] 1.1 Add optional `startDate` (`yyyy-mm-dd`) query parameter to `POST /api/calendar/sync` in `CalendarController`
- [x] 1.2 Keep backward compatibility in controller flow so calls without `startDate` continue using current behavior and response contract
- [x] 1.3 Update endpoint OpenAPI annotations/examples to document optional `startDate`

## 2. Sync Service Flow and State Safety

- [x] 2.1 Introduce service entrypoint/overload that receives optional `startDate` without removing current `synchronize(Long userId)` behavior
- [x] 2.2 Implement dedicated startDate-based sync path that fetches events from the informed date onward
- [x] 2.3 Ensure startDate-based sync does not overwrite persisted incremental `syncToken` in `SyncState`
- [x] 2.4 Preserve existing incremental/full/fallback semantics for calls without `startDate`

## 3. Google Calendar Client Support

- [x] 3.1 Extend `GoogleCalendarClient` fetch API to accept optional start-date lower bound
- [x] 3.2 Apply Google request date lower bound only when startDate-based sync is requested, keeping current paging/error handling
- [x] 3.3 Keep current fetch behavior unchanged for existing sync calls that do not use `startDate`

## 4. Regression and Validation

- [x] 4.1 Add/adjust `CalendarSyncService` tests for sync with and without `startDate`
- [x] 4.2 Add test proving existing persisted `syncToken` remains unchanged after startDate-based sync
- [x] 4.3 Add/adjust `GoogleCalendarClient` tests validating lower-bound date request when `startDate` is provided
- [x] 4.4 Run targeted test suite for calendar sync contract to confirm `200` response shape and existing `403` mappings remain unchanged
