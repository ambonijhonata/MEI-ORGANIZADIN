## 1. Baseline e Ambiente de Validacao

- [x] 1.1 Definir ambiente de benchmark com limites equivalentes a `512MB RAM` e `0.1 CPU`.
- [ ] 1.2 Executar baseline atual do `POST /api/calendar/sync` com conta de 14.482 eventos e registrar `google_fetch_ms`, `db_lookup_ms`, `processing_ms`, `db_write_ms`, `sync_total_ms`.
- [ ] 1.3 Consolidar baseline (p50/p95/p99) e identificar etapa dominante para guiar a fase 2.

## 2. Fase 1 de Implementacao (Memoria e Fluxo)

- [x] 2.1 Refatorar fluxo de fetch/processamento para pipeline paginado, evitando acumulo integral de eventos em memoria.
- [x] 2.2 Ajustar limites transacionais para reduzir janela de transacao longa durante sync pesado.
- [x] 2.3 Validar preservacao de semantica funcional (`syncToken`, fallback `410`, contadores `created/updated/deleted`) com testes automatizados.

## 3. Fase 2 de Implementacao (Lookup e Persistencia Delta)

- [x] 3.1 Introduzir lookup local mais leve para decisao de no-op, reduzindo preload com associacoes pesadas quando nao necessario.
- [x] 3.2 Otimizar persistencia orientada a delta efetivo para reduzir `db_write_ms` em alto volume.
- [x] 3.3 Testar cenarios de equivalencia de associacoes e eventos inalterados para garantir ausencia de regressao de dados.

## 4. Validacao de Meta e Decisao de Rollout

- [ ] 4.1 Reexecutar benchmark no ambiente-alvo com dataset de 14.482 eventos apos as fases 1 e 2.
- [ ] 4.2 Confirmar objetivo `sync_total_ms <= 10000`; se nao atingir, documentar mitigacao aprovada (nova iteracao tecnica ou limite operacional aceito).
- [ ] 4.3 Revisar impacto de UX e confirmar comportamento nao bloqueante no app durante sync pesado.

## 5. Rollout Seguro e Rollback

- [x] 5.1 Externalizar e revisar parametros de tuning por ambiente (`GOOGLE_CALENDAR_SYNC_MAX_RESULTS`, `CALENDAR_SYNC_BATCH_SIZE`, `CALENDAR_SYNC_BATCH_CLEAR_ENABLED`, `POSTGRES_REWRITE_BATCHED_INSERTS`).
- [ ] 5.2 Executar rollout gradual com observabilidade ativa de tempos por etapa e erros de sync.
- [x] 5.3 Validar procedimento de rollback por configuracao sem alteracao de contrato dos endpoints.
