## 1. Session-Safe Sync Refactor

- [x] 1.1 Extrair no `CalendarSyncService` uma etapa transacional por chunk para lookup, processamento e persistencia das mutacoes do sync.
- [x] 1.2 Garantir que eventos existentes sejam carregados com `serviceLinks` e demais associacoes necessarias antes de qualquer chamada a `associateServices` ou `clearServiceAssociation`.
- [x] 1.3 Remover do hot path qualquer mutacao de colecao JPA em entidades detached.

## 2. Transaction Boundary And Reconciliation Integrity

- [x] 2.1 Ajustar os caminhos `incremental`, `full sync` e `startDate sync` para usar a mesma fronteira transacional curta e segura.
- [x] 2.2 Garantir que cleanup, delecao em lote, `flush` e reconciliacao continuem executando com `EntityManager` ativo.
- [x] 2.3 Preservar semantica atual dos contadores `created`, `updated` e `deleted` apos a refatoracao.

## 3. Regression Coverage

- [x] 3.1 Adicionar teste que reproduza o cenario real: evento existente com `serviceLinks` lazy e sync atualizando associacao de servicos sem `LazyInitializationException`.
- [x] 3.2 Cobrir com testes os caminhos `incremental`, `full sync` e `startDate sync` para garantir que a correcao nao fique limitada a um unico fluxo.
- [x] 3.3 Validar que falhas internas reais continuam marcando `SYNC_FAILED` sem mascarar o erro como problema de autenticacao.

## 4. Operational Validation

- [x] 4.1 Executar suite alvo de sync e registrar evidencias.
- [ ] 4.2 Validar em staging/producao controlada com conta que tenha multi-servicos e eventos preexistentes.
- [ ] 4.3 Monitorar logs e confirmar ausencia de `LazyInitializationException` e `TransactionRequiredException` apos deploy.
