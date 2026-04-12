# Calendar Sync Rollback Plan

This plan describes how to disable optimization strategies for `POST /api/calendar/sync` without changing API contracts.

## Rollback Controls

- Batch write tuning:
- `CALENDAR_SYNC_BATCH_SIZE`
- `HIBERNATE_BATCH_SIZE`
- `HIBERNATE_ORDER_INSERTS`
- `HIBERNATE_ORDER_UPDATES`
- Persistence context clearing:
- `CALENDAR_SYNC_BATCH_CLEAR_ENABLED`
- Google fetch tuning:
- `GOOGLE_CALENDAR_SYNC_MAX_RESULTS`
- `GOOGLE_CALENDAR_SYNC_FIELDS`

## Safe Rollback Procedure

1. Keep deployed code version unchanged.
2. Reconfigure environment variables to conservative values:
- Disable aggressive tuning (for example, smaller batch sizes).
- Keep `CALENDAR_SYNC_BATCH_CLEAR_ENABLED=false` unless explicitly validated.
3. Restart API instances with updated config.
4. Monitor sync error rate and latency metrics.
5. If needed, roll back deployment to previous release while preserving database schema.

## Contract Safety

- No rollback step above changes:
- endpoint path/method,
- HTTP status mapping,
- success payload shape,
- error payload shape/codes.

The app remains compatible during rollback.
