# Calendar Sync Service Constructor Fix - Acceptance and Rollback

## Acceptance Criteria

- API startup completes without bean creation failure for `calendarSyncService`.
- `run.bat` no longer fails with `No default constructor found` for `CalendarSyncService`.
- Sync contract remains unchanged:
- `POST /api/calendar/sync` success returns HTTP `200` with `created`, `updated`, `deleted`.
- Existing `403` error mappings remain unchanged (`INTEGRATION_REVOKED`, `GOOGLE_API_FORBIDDEN`).
- Incremental sync fallback to full resync on token expiration (`410`) remains unchanged.

## Verification Steps

1. Execute focused regression tests for sync and related error handling.
2. Start the API through `run.bat` and confirm Spring context initialization succeeds.
3. Execute smoke call(s) to `POST /api/calendar/sync` in local/dev environment.

## Rollback Plan

If any regression occurs after deployment:

1. Revert constructor-instantiation changes in `CalendarSyncService`.
2. Redeploy previous stable build.
3. Re-run sync contract smoke checks (`200` shape, `403` mappings, 410 fallback).

This rollback does not require schema/database changes.
