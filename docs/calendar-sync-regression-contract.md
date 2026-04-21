# Calendar Sync Regression Contract

This document captures the current expected behavior of `POST /api/calendar/sync` and should be used as a regression baseline during performance tuning.

## Endpoint Contract

- Method: `POST`
- Path: `/api/calendar/sync`
- Success response: `200 OK`
- Success payload shape:

```json
{
  "created": 0,
  "updated": 0,
  "deleted": 0
}
```

## Error Contract

- Revoked integration:
- HTTP: `403`
- Error code: `INTEGRATION_REVOKED`
- Google API forbidden/access denied:
- HTTP: `403`
- Error code: `GOOGLE_API_FORBIDDEN`
- Generic I/O failure:
- Sync state is marked failed (`IO_ERROR`) and runtime failure is propagated.

## Behavioral Invariants

- Sync token behavior:
- If incremental token is valid, run incremental sync.
- If Google returns token expiration (`410`), fallback to full resync.
- Incremental Google request behavior:
- Token-based incremental requests explicitly use `showDeleted=true` and do not apply `orderBy=startTime`.
- Counter semantics:
- `created`: number of new local events persisted.
- `updated`: number of existing local events updated.
- `deleted`: number of local calendar events removed from persistence by sync.
- Cancelled Google events returned in incremental mode delete matching local events; cancelled markers without a local event do not increment `deleted`.
- Full-resync fallback after Google sync token expiration (`410`) reconciles local events by deleting local Google event ids that are absent from the completed fallback result set.
- Initial full sync (no token) and initial start-date sync (`startDate` without token) also reconcile local Google-backed rows for their synchronized scope.
- Start-date reconciliation is bounded to local events with `eventStart >= startDate` and does not remove rows before the configured boundary.
- Deletion persistence order is explicit: payment rows for deleted appointments are removed before appointment rows in the same transaction.
- Cleanup reconciliation ignores local rows without `googleEventId`.
- Matching semantics:
- Event title parsing and client/service resolution behavior remains unchanged.
- Existing HTTP payload shapes and status codes must remain unchanged.
- Android Home requires no UI changes: it continues to read `GET /api/calendar/events` after sync against the reconciled backend state.
