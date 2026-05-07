## Why

O endpoint `POST /api/calendar/sync` falha em produção com `TransactionRequiredException` durante o cleanup de eventos, interrompendo sincronizações de alto volume e degradando a experiência do usuário. O erro ocorre no caminho de deleção em lote com `flush` sem transação ativa, então precisa ser corrigido agora para restaurar confiabilidade do sync.

## What Changes

- Garantir fronteira transacional explícita para o fluxo de persistência do sync (incluindo cleanup/deleção em lote e `flush`).
- Ajustar a organização do fluxo de sync para que operações JPA mutáveis rodem somente dentro de contexto transacional válido.
- Preservar contrato da API (`created/updated/deleted`, códigos de erro de autenticação e integração) sem mudanças de payload.
- Adicionar validações automatizadas para cenário com deleção em lote e evitar regressão de `No EntityManager with actual transaction available`.

## Capabilities

### New Capabilities
- `calendar-sync-transaction-boundary`: Define comportamento transacional obrigatório para operações de escrita/flush do sync, inclusive cleanup inicial/full sync.

### Modified Capabilities
- `calendar-sync-startdate-filter`: Garantir que o caminho de sync com e sem `startDate` mantenha persistência consistente em transação durante reconciliação e deleção.

## Impact

- Backend API: `CalendarSyncService` e repositórios de eventos/pagamentos/links.
- Execução JPA/Hibernate: ciclo de vida de `EntityManager`, `flush` e operações `@Modifying` no sync.
- Testes: suites de sync (unit/integration) cobrindo cleanup com deleção e cenários de alto volume.
- Operação: redução de falhas 500 no `POST /api/calendar/sync` em produção.
