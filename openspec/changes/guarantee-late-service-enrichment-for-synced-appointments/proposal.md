## Why

O enriquecimento introduzido para servicos cadastrados apos a sincronizacao cobre apenas o caso em que o gatilho do catalogo roda com sucesso no momento do cadastro. Na pratica, agendamentos ja parcialmente sincronizados podem continuar incompletos indefinidamente, porque a sync incremental nao revisita eventos sem delta no Google e o sistema nao persiste uma pendencia de reenriquecimento para retry ou backfill.

## What Changes

- Persist a per-user signal that the service catalog changed in a way that may enrich already synchronized appointments.
- Make calendar synchronization consume that signal and run local appointment enrichment even when Google returns no event changes.
- Add a reliable retry/backfill path so partially recognized legacy appointments are eventually re-evaluated against the current catalog.
- Keep Google synchronization and catalog enrichment as separate concerns: service creation should not require a full Google resync to repair old appointments.
- Add regression coverage for legacy partially identified appointments and for repeated syncs after late service creation.

## Capabilities

### New Capabilities

### Modified Capabilities
- `calendar-sync`: synchronization must also reconcile pending late-service enrichment for already synchronized appointments, even without Google-side deltas.
- `service-catalog`: catalog changes that can create new title matches must persist and trigger a reliable enrichment/backfill workflow instead of relying on a single best-effort async call.

## Impact

- Backend calendar synchronization orchestration and sync-state persistence.
- Service catalog create/update flow and its post-change triggers.
- Retry and backfill behavior for synchronized appointments of existing users, including `userId=4` and other unreported legacy cases.
- `src/main/java/com/api/calendar/CalendarSyncService.java`
- `src/main/java/com/api/calendar/CalendarEventReprocessor.java`
- `src/main/java/com/api/calendar/SyncState.java`
- `src/main/java/com/api/servicecatalog/ServiceCatalogService.java`
- Backend regression tests around late-created services, sync retries, and legacy appointment enrichment.
