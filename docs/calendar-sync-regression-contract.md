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
- Counter semantics:
- `created`: number of new local events persisted.
- `updated`: number of existing local events updated.
- `deleted`: number of cancelled events returned by Google in incremental mode.
- Matching semantics:
- Event title parsing and client/service resolution behavior remains unchanged.
- Existing HTTP payload shapes and status codes must remain unchanged.
