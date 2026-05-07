## Why

O fluxo atual permite que um mesmo agendamento receba links de servico duplicados em `calendar_event_services`, o que distorce o relatorio de fluxo de caixa mesmo quando o faturamento continua correto. O problema ficou evidente no uso real de 05/05/2026, quando um usuario sincronizou eventos antes de cadastrar servicos e depois acionou novo processamento ao cadastrar o catalogo.

## What Changes

- Make calendar service association idempotent across initial sync, incremental sync, and unidentified-event reprocessing.
- Serialize per-user service-association mutations so background sync and post-catalog reprocessing cannot write duplicate links for the same event concurrently.
- Add database-level uniqueness protection for event/service links and fail-safe write behavior that preserves a single canonical set of links per event.
- Add an operational remediation step with SQL to remove duplicated `calendar_event_services` rows already persisted in production before enabling the new constraint.
- Add regression coverage for the real-world sequence: sync without services, service creation, async reprocessing, later sync, and cash-flow generation.

## Capabilities

### New Capabilities

### Modified Capabilities
- `calendar-appointment-recognition-and-payment`: service association persistence and unidentified-event reprocessing MUST prevent duplicate service links across repeated or concurrent processing.

## Impact

- Backend calendar sync and event reprocessing behavior.
- Database schema and migration strategy for `calendar_event_services`.
- Production data remediation for already duplicated rows.
- `API/src/main/java/com/api/calendar/CalendarSyncService.java`
- `API/src/main/java/com/api/calendar/CalendarEventReprocessor.java`
- `API/src/main/java/com/api/calendar/CalendarEvent.java`
- `API/src/main/resources/db/migration/*`
- Backend tests covering sync, reprocessing, and financial-report integrity.
