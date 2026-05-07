## 1. Transaction Boundary Hardening

- [x] 1.1 Definir e aplicar fronteira transacional explicita no fluxo de persistencia do `CalendarSyncService` para garantir `EntityManager` ativo em mutacoes e flush.
- [x] 1.2 Isolar/validar o caminho de cleanup inicial/full sync para que delecao em lote e flush executem dentro da mesma transacao.
- [x] 1.3 Revisar operacoes `@Modifying` usadas no sync para confirmar compatibilidade com a nova fronteira transacional.

## 2. Error Handling And Contract Integrity

- [x] 2.1 Garantir que falhas internas de persistencia atualizem estado de sync como `SYNC_FAILED` com categoria interna antes da resposta.
- [x] 2.2 Confirmar que erro interno de persistencia nao e mascarado como `UNAUTHORIZED` no endpoint `POST /api/calendar/sync`.
- [x] 2.3 Preservar payload e semantica do sucesso (`created/updated/deleted`) sem breaking change.

## 3. Regression Validation

- [x] 3.1 Adicionar/ajustar testes de sync cobrindo cenarios de delecao+flush no full sync e incremental.
- [x] 3.2 Adicionar/ajustar testes para fluxos com `startDate` garantindo integridade transacional e contrato de erro.
- [x] 3.3 Executar suite alvo (`CalendarSyncService*`, testes de erro/seguranca) e registrar evidencias.

## 4. Rollout Readiness

- [ ] 4.1 Validar em staging com conta de alto volume que o erro `No EntityManager with actual transaction available` nao ocorre mais.
- [x] 4.2 Atualizar runbook de monitoramento com sinais de falha transacional vs falha de autenticacao.
- [x] 4.3 Definir passo de rollback seguro (reversao do ajuste transacional + validacao pos-rollback).
