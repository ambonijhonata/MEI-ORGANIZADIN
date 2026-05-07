## Why

O endpoint `POST /api/calendar/sync` ainda excede o tempo aceitável para contas de alto volume e causa timeout percebido pelo app. Precisamos de um plano de execução objetivo, faseado e mensurável para atingir `<=10s` com segurança no ambiente restrito (`512MB RAM`, `0.1 CPU`).

## What Changes

- Definir um plano técnico em fases para reduzir latência de sync sem alterar contrato HTTP atual.
- Priorizar otimizações compatíveis com baixo recurso: processamento paginado/streaming, redução de memória transitória, e escrita em lote mais eficiente.
- Estabelecer critérios de aceite por etapa com métricas obrigatórias (`google_fetch_ms`, `db_lookup_ms`, `processing_ms`, `db_write_ms`, `sync_total_ms`).
- Formalizar estratégia de rollout/rollback por configuração para minimizar risco operacional.
- Garantir preservação de semântica existente (`syncToken`, fallback `410`, contadores `created/updated/deleted`).

## Capabilities

### New Capabilities
- `calendar-sync-low-resource-performance-execution`: Define requisitos de execução, validação e rollout para atingir meta de desempenho do sync em ambiente de baixo recurso.

### Modified Capabilities
- `calendar-stale-while-revalidate`: reforçar requisito de UX não bloqueante durante períodos de sincronização pesada.

## Impact

- Backend API:
- `src/main/java/com/api/calendar/CalendarSyncService.java`
- `src/main/java/com/api/google/GoogleCalendarClient.java`
- `src/main/java/com/api/calendar/CalendarEventRepository.java`
- Configuração:
- `src/main/resources/application.yml`
- OpenSpec:
- `openspec/changes/calendar-sync-ten-second-execution-plan/*`
- `openspec/specs/calendar-stale-while-revalidate/spec.md` (delta)

