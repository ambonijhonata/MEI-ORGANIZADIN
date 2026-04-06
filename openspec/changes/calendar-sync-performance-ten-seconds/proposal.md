## Why

Mesmo após otimizações recentes, o `POST /api/calendar/sync` ainda leva ~23 segundos para sincronizar 14.354 eventos em conta de alto volume, ultrapassando o tempo tolerado pelo app e disparando timeout com aviso de sincronização parcial. Precisamos atingir meta operacional de no máximo 10 segundos sem alterar o comportamento funcional atual da API.

## What Changes

- Aplicar tuning imediato de configuração para reduzir latência de paginação Google e overhead de escrita em lote:
- `GOOGLE_CALENDAR_SYNC_MAX_RESULTS=2500`
- `CALENDAR_SYNC_BATCH_SIZE=1000`
- `CALENDAR_SYNC_BATCH_CLEAR_ENABLED=true`
- JDBC PostgreSQL com `reWriteBatchedInserts=true`
- Otimizar hot path de full sync:
- substituir lookup `IN` gigante por preload de eventos do usuário;
- adicionar cache local de normalização (`raw -> normalized`) por execução;
- reduzir custo de regex em parsing/normalização com abordagens pré-compiladas/manuais.
- Evitar trabalho desnecessário por evento:
- pular persistência quando evento não mudou de fato;
- evitar recomputar associação de serviços quando associação já está equivalente.
- Manter invariantes de compatibilidade:
- mesmo contrato HTTP/payload;
- mesma semântica de `syncToken`, fallback de full resync e contadores de resultado.

## Capabilities

### New Capabilities
- `calendar-sync-ten-second-target`: Define requisitos técnicos para reduzir o tempo de sincronização de alto volume para <=10 segundos sem alterar comportamento funcional da API.

### Modified Capabilities
- Nenhuma.

## Impact

- Código backend principal:
- `src/main/java/com/api/calendar/CalendarSyncService.java`
- `src/main/java/com/api/google/GoogleCalendarClient.java`
- `src/main/java/com/api/calendar/CalendarEventRepository.java` (e possiveis queries auxiliares)
- `src/main/java/com/api/servicecatalog/ServiceDescriptionNormalizer.java`
- `src/main/java/com/api/calendar/EventTitleParser.java`
- Configuração:
- `src/main/resources/application.yml`
- conexão PostgreSQL (URL/propriedades JDBC)
- Testes e validação:
- suites de regressão funcional do sync;
- medição comparativa baseline vs otimizado para cenário de 14.354 eventos.
