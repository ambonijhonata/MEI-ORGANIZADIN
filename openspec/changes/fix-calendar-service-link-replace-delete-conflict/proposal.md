## Why

O fluxo atual de replace dos `calendar_event_services` ainda combina duas estrategias de remocao para os mesmos links: limpeza da colecao JPA com `orphanRemoval` e `DELETE` explicito em lote no repositorio. Em producao isso continua quebrando sincronizacoes reais, primeiro com violacao de unicidade e depois com `ObjectOptimisticLockingFailureException` ao tentar deletar novamente linhas ja removidas.

## What Changes

- Make service-link replacement use a single canonical removal strategy for persisted calendar events instead of mixing JPA orphan removal with explicit bulk delete.
- Separate domain behavior for `replace` and `enrich` so synchronized events that need a full association swap do not schedule implicit orphan deletes alongside repository-driven cleanup.
- Add regression coverage for the real production sequence where an existing synchronized event enters the replace path and must complete without duplicate insertions or stale delete failures.
- Document the production-facing remediation and validation path for redeploying the API and re-running synchronization on the affected account.

## Capabilities

### New Capabilities

### Modified Capabilities
- `calendar-appointment-recognition-and-payment`: persisted service-link replacement MUST converge through one canonical removal path so repeated sync or reprocessing cannot fail due to duplicate delete responsibilities.

## Impact

- Backend calendar sync and event reprocessing persistence flow.
- JPA mapping and repository responsibilities for `calendar_event_services`.
- `src/main/java/com/api/calendar/CalendarEvent.java`
- `src/main/java/com/api/calendar/CalendarSyncService.java`
- `src/main/java/com/api/calendar/CalendarEventReprocessor.java`
- `src/main/java/com/api/calendar/CalendarEventServiceLinkRepository.java`
- `src/test/java/com/api/calendar/*`
- Backend regression tests around service-link replacement and synchronization stability.
