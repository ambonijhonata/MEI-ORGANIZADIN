## 1. Root Cause Fix In Calendar Sync

- [x] 1.1 Refatorar comparacao de associacao de servicos em `CalendarSyncService` para evitar acesso lazy invalido (sem dereferenciar proxy fora de contexto).
- [x] 1.2 Revisar comparacao de cliente/servico para usar identidade segura (id/snapshot/preload) no hot path.
- [x] 1.3 Garantir que o fluxo de reconciliacao/delecao mantenha semantica atual apos ajuste lazy-safe.

## 2. Error Classification And Sync State Integrity

- [x] 2.1 Tratar `RuntimeException` no fluxo de `synchronize` para persistir estado `FAILED` com categoria/mensagem antes de propagar resposta.
- [x] 2.2 Garantir que `SYNCING` nao fique persistido em falha interna (estado final consistente em sucesso/falha).
- [x] 2.3 Ajustar testes para cobrir transicao de estado em erro interno e evitar regressao funcional.

## 3. Security/Error Surface Integrity

- [x] 3.1 Ajustar configuracao de seguranca e/ou filtro de autenticacao para nao mascarar falha interna autenticada como `UNAUTHORIZED` generico.
- [x] 3.2 Cobrir com testes de integracao/contrato que requests autenticados com falha interna retornam classificacao de erro de aplicacao.

## 4. Regression Validation

- [x] 4.1 Executar suites de testes de sync e seguranca relacionadas ao endpoint `POST /api/calendar/sync`.
- [ ] 4.2 Executar benchmark comparativo no cenario de alto volume e registrar `google_fetch_ms`, `db_lookup_ms`, `processing_ms`, `db_write_ms`, `sync_total_ms`.
- [ ] 4.3 Confirmar que a correcao elimina `LazyInitializationException` sem regressao significativa de latencia e documentar mitigacao caso haja regressao.

## 5. Rollout And Operational Readiness

- [x] 5.1 Atualizar runbook com sinais de monitoramento (falhas internas de sync vs falhas reais de autenticacao).
- [x] 5.2 Definir procedimento de rollback para a correcao (codigo + configuracao) sem alterar contrato da API.
