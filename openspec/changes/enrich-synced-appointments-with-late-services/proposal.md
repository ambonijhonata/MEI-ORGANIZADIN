## Why

O fluxo atual reconhece apenas os servicos disponiveis no momento da sincronizacao ou do reprocessamento de eventos nao identificados. Isso deixa agendamentos parcialmente identificados congelados com um subconjunto de servicos, mesmo depois que o usuario cadastra os servicos faltantes no catalogo.

## What Changes

- Enrich already-synchronized appointments when new services are created or renamed so previously missing service tokens can be associated without depending on a new Google-side event change.
- Expand post-catalog reprocessing beyond `identified=false` events so partially identified appointments are re-evaluated against the current service catalog.
- Preserve historical snapshots for services that were already associated correctly while adding only the newly recognized services that were missing before.
- Add regression coverage for the real-world sequence: partial match on initial sync, later service creation, enrichment of the existing appointment, and stable follow-up synchronization.

## Capabilities

### New Capabilities

### Modified Capabilities
- `calendar-sync`: service reprocessing after catalog changes MUST enrich already synchronized appointments that still have unmatched service tokens in their saved titles.
- `service-catalog`: service creation or description updates MUST trigger enrichment of synchronized appointments that can gain new service associations from the catalog change.

## Impact

- Backend calendar synchronization and post-catalog reprocessing behavior.
- Historical service snapshot preservation rules during appointment enrichment.
- `src/main/java/com/api/calendar/CalendarEventReprocessor.java`
- `src/main/java/com/api/calendar/CalendarSyncService.java`
- `src/main/java/com/api/calendar/CalendarEvent.java`
- `src/main/java/com/api/servicecatalog/ServiceCatalogService.java`
- Backend tests covering sync, reprocessing, and follow-up synchronization stability.
