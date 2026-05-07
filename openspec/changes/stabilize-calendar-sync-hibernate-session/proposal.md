## Why

O endpoint `POST /api/calendar/sync` falha em producao com `LazyInitializationException` ao tentar mutar `CalendarEvent.serviceLinks` fora de uma sessao Hibernate ativa. O problema aparece no fluxo de atualizacao de eventos existentes, interrompe o sync com erro `500`, persiste estado `SYNC_FAILED` e degrada a confiabilidade da sincronizacao.

As correcoes ja discutidas no repositorio tratam partes do problema separadamente (lazy safety e fronteira transacional), mas a falha atual mostra que a abordagem mais segura precisa consolidar os dois lados: garantir que entidades mutadas estejam `managed` quando `serviceLinks` for alterado e manter todas as mutacoes JPA dentro de transacoes explicitas e curtas.

## What Changes

- Reestruturar o `CalendarSyncService` para separar claramente fase externa de fetch Google e fase interna de lookup/processamento/persistencia em contexto transacional controlado.
- Garantir que eventos existentes sujeitos a mutacao sejam recarregados por chunk com associacoes necessarias (`serviceLinks` e referencias usadas no diff) antes de chamar `associateServices` ou `clearServiceAssociation`.
- Eliminar o uso de entidades detached com colecoes lazy nao inicializadas no hot path do sync.
- Preservar contrato funcional do endpoint (`created`, `updated`, `deleted`, codigos de erro existentes) sem depender de `open-in-view`.
- Adicionar regressao automatizada que reproduza exatamente o cenario de producao: evento existente com `serviceLinks` lazy, sync incremental e atualizacao de associacao.

## Capabilities

### New Capabilities
- `calendar-sync-session-safe-association-mutation`: Define que mutacoes de associacoes de servico no sync devem ocorrer apenas com entidades gerenciadas e associacoes necessarias carregadas.

### Modified Capabilities
- `calendar-sync-transaction-boundary`: Refinar a fronteira transacional para cobrir nao apenas `flush` e delecao, mas tambem a fase de processamento que muta colecoes JPA.
- `calendar-sync-startdate-filter`: Garantir que o caminho com `startDate` use o mesmo modelo seguro de lookup/mutacao por chunk.

## Impact

- Backend API: `CalendarSyncService`, `CalendarEventRepository` e possivelmente metodos auxiliares de lookup/persistencia por chunk.
- JPA/Hibernate: ciclo de vida de entidades, colecoes lazy `serviceLinks`, dirty checking e `EntityManager` por transacao.
- Testes: suites de sync com cobertura para incremental, full sync e start-date sync usando dados reais de associacao multi-servico.
- Operacao: eliminacao do `LazyInitializationException` observado hoje e reducao de 500 no sync sem reabrir `open-in-view`.
