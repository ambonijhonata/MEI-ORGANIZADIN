## Why

O endpoint `POST /api/calendar/sync` apresenta tempo de resposta muito alto em contas com grande volume de eventos (ex.: 14.354 agendamentos), chegando a minutos e causando timeout no aplicativo Android. Isso afeta a experiencia do usuario com avisos de sincronizacao parcial e reduz a confianca nos dados exibidos.

## What Changes

- Otimizar exclusivamente o fluxo interno do endpoint `POST /api/calendar/sync` para reduzir o tempo total de sincronizacao em cenarios de alto volume.
- Reduzir custo de I/O e processamento por evento durante o sync (buscas repetitivas, persistencia item a item e sobrecarga de parsing/matching).
- Melhorar eficiencia da leitura de eventos na Google Calendar API (menos overhead por pagina/chamada, mantendo semantica de full e incremental sync).
- Adicionar instrumentacao e metricas de etapa para identificar gargalos de rede Google x banco x processamento.
- Preservar todos os comportamentos atuais da API:
- contrato HTTP do endpoint (`200`, `403`, payloads de sucesso/erro);
- semantica de `syncToken`, incluindo fallback para full resync em expiracao;
- regras de identificacao de cliente/servico, snapshots e contagem `created/updated/deleted`.

## Capabilities

### New Capabilities
- `calendar-sync-performance`: Define requisitos de desempenho e observabilidade para o processamento do endpoint `POST /api/calendar/sync` sem alterar comportamento funcional.

### Modified Capabilities
- Nenhuma.

## Impact

- Backend API:
- `src/main/java/com/api/calendar/CalendarSyncService.java`
- `src/main/java/com/api/google/GoogleCalendarClient.java`
- `src/main/java/com/api/calendar/CalendarEventRepository.java` (e possiveis repositorios auxiliares relacionados ao sync)
- `src/main/resources/application.yml` (parametros de tuning de sync/hibernate, se necessario)
- Potenciais ajustes de banco (indices/tuning) estritamente para melhorar desempenho do sync.
- Testes:
- novos/ajustados testes de performance orientados a volume e regressao comportamental do endpoint.
- Sem alteracao de contrato para o app Android e sem mudancas em outros endpoints.
