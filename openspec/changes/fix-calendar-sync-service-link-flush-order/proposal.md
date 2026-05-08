## Why

O fluxo atual de `POST /api/calendar/sync` ainda falha em produção para algumas contas reais quando o mesmo chunk mistura eventos deletados com eventos que precisam substituir `calendar_event_services`. Nessa combinação, uma operação de limpeza intermediária força flush antecipado e o Hibernate tenta inserir o novo vínculo de serviço antes de remover o antigo, gerando `duplicate key` e erro 500 para o usuário.

## What Changes

- Stabilize the persistence order of calendar sync mutations when the same transaction contains deletions and persisted-event service-link replacement.
- Ensure service-link replacement happens through a deterministic delete-before-flush path so existing `(calendar_event_id, service_id)` rows are removed before any replacement insert is emitted.
- Prevent unrelated cleanup steps, such as payment deletion or reconciliation writes, from triggering an autoflush that surfaces stale `calendar_event_services` rows during the same chunk.
- Add regression coverage for the real production sequence where a chunk contains both deleted events and an updated event that replaces recognized services.

## Capabilities

### New Capabilities
- `calendar-sync-mutation-order-stability`: guarantees deterministic persistence ordering for sync chunks that combine deletions, cleanup steps, and replacement of persisted calendar service links.

### Modified Capabilities
None.

## Impact

- Backend calendar sync persistence flow and transaction sequencing.
- `src/main/java/com/api/calendar/CalendarSyncService.java`
- `src/main/java/com/api/calendar/CalendarEventPaymentRepository.java`
- `src/main/java/com/api/calendar/CalendarEventServiceLinkRepository.java`
- `src/main/java/com/api/calendar/CalendarEventServiceLinkBulkRepositoryImpl.java`
- Backend regression tests around mixed sync mutations and replace/delete ordering.
- No external API payload changes are intended; the impact is on reliability of the existing sync endpoint.
