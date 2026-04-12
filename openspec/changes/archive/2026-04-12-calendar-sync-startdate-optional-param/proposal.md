## Why

O endpoint `POST /api/calendar/sync` hoje sempre executa a sincronizacao sem filtro inicial de data, o que dificulta cenarios em que o usuario quer sincronizar apenas a partir de uma data de corte. Precisamos adicionar `startDate` opcional sem quebrar o contrato atual nem alterar o comportamento existente quando o parametro nao for informado.

## What Changes

- Adicionar suporte ao query param opcional `startDate` no endpoint `POST /api/calendar/sync`, no formato `yyyy-mm-dd`.
- Quando `startDate` for informado, executar sincronizacao considerando eventos a partir da data recebida.
- Quando `startDate` nao for informado, preservar integralmente o fluxo atual (incremental/full/fallback) e os mesmos resultados funcionais.
- Preservar contrato HTTP atual do endpoint (`200` com `created/updated/deleted`, mapeamentos `403` existentes).
- Garantir como regra de seguranca que sincronizacoes com `startDate` nao sobrescrevem o `syncToken` incremental principal do usuario.

## Capabilities

### New Capabilities
- `calendar-sync-startdate-filter`: Permite disparar sincronizacao manual com filtro opcional por data inicial em `POST /api/calendar/sync`, mantendo compatibilidade retroativa.

### Modified Capabilities
- None.

## Impact

- Backend API:
- `src/main/java/com/api/calendar/CalendarController.java`
- `src/main/java/com/api/calendar/CalendarSyncService.java`
- `src/main/java/com/api/google/GoogleCalendarClient.java`
- Testes de regressao/novos cenarios:
- `src/test/java/com/api/calendar/CalendarSyncServiceTest.java`
- `src/test/java/com/api/calendar/CalendarSyncServiceExtendedTest.java`
- `src/test/java/com/api/google/GoogleCalendarClientTest.java`
- Sem mudanca obrigatoria no app cliente existente (parametro e opcional).
