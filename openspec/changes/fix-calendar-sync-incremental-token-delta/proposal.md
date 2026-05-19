## Why

A sincronizacao da agenda continua retornando volume muito alto em chamadas consecutivas, indicando que o fluxo incremental com `syncToken` nao esta se mantendo estavel em producao. Isso degrada latencia, consumo de CPU/memoria em ambiente restrito (0.1 CPU / 512MB) e gera telemetria enganosa no payload de resposta.

## What Changes

- Garantir que o fluxo de sync incremental preserve e reutilize `syncToken` de forma consistente entre execucoes bem-sucedidas.
- Remover condicoes de requisicao ao Google Calendar que possam impedir emissao/renovacao de `nextSyncToken` em full sync.
- Adicionar protecao explicita para nao sobrescrever `sync_token` persistido com valor nulo quando nao houver token novo valido na resposta.
- Ajustar a semantica dos contadores de resultado para que `updated` represente apenas eventos efetivamente alterados/persistidos.
- Melhorar logs/observabilidade para distinguir claramente sync incremental, full resync por expiracao (`410`) e fallback por ausencia de token.

## Capabilities

### New Capabilities
- `calendar-sync-token-integrity`: garante aquisicao, persistencia e reutilizacao correta de `syncToken`, com fallback controlado e observavel.
- `calendar-sync-accurate-counters`: garante que `created/updated/deleted` representem mutacoes reais aplicadas no banco.

### Modified Capabilities
- `calendar-sync-startdate-filter`: preservar comportamento de `startDate` sem quebrar o fluxo incremental e sem forcar full sync recorrente indevido.

## Impact

- Backend: `CalendarSyncService`, `GoogleCalendarClient`, `SyncState` e testes de sync.
- API: `POST /api/calendar/sync` (sem breaking change de payload; apenas correcao de semantica de contagem).
- Operacao: reducao de volume processado por sync recorrente e melhoria da acuracia de metricas/logs.
