## Context

O endpoint `POST /api/calendar/sync` ja foi otimizado, mas ainda apresenta ~23 segundos para sincronizar 14.354 eventos em conta de alto volume. O app Android interpreta esse tempo como timeout e exibe sincronizacao parcial indisponivel. A meta desta change e reduzir para no maximo 10 segundos nesse cenario, sem alterar contrato HTTP, semântica de negocio ou cobertura de dados sincronizados.

A change deve atuar em tres frentes:
1. tuning de configuracao imediata;
2. otimizacao de hot path de full sync (lookup e CPU);
3. eliminacao de trabalho inutil (persistencia/associacao sem mudanca real).

## Goals / Non-Goals

**Goals:**
- Atingir tempo maximo de 10s para sincronizacao de 14.354 eventos no ambiente-alvo de validacao.
- Reduzir round-trips e custo de CPU no fluxo de sync sem perder eventos.
- Preservar comportamento funcional e contrato da API.
- Introduzir criterios de validacao claros para regressao de dados e performance.

**Non-Goals:**
- Alterar endpoint, payloads, codigos HTTP ou semantica de erro.
- Alterar regras de parsing/matching de negocio.
- Mudar arquitetura para processamento assíncrono nesta change.

## Decisions

### 1) Tuning de configuracao com foco em throughput

Decisao:
- Ajustar parametros de paginação Google e persistencia em lote:
- `GOOGLE_CALENDAR_SYNC_MAX_RESULTS=2500`
- `CALENDAR_SYNC_BATCH_SIZE=1000`
- `CALENDAR_SYNC_BATCH_CLEAR_ENABLED=true`
- JDBC PostgreSQL com `reWriteBatchedInserts=true`

Rationale:
- Menos paginas Google e menos flushes por execucao reduzem overhead total.
- `reWriteBatchedInserts` melhora eficiencia de insert/update em lote no driver PostgreSQL.

Alternativas consideradas:
- Manter defaults atuais: custo alto de paginação e flush frequente.
- Aumentar batch indefinidamente: risco de memoria e lock contention.

### 2) Full sync com preload em vez de `IN` gigante

Decisao:
- Em full sync, carregar eventos locais do usuario em um unico preload por usuario (mapa por `googleEventId`), evitando query com `IN` de milhares de IDs.
- Manter incremental usando lookup direcionado por IDs retornados, mas com fallback para estratégia mais eficiente caso cardinalidade cresca.

Rationale:
- `IN` muito grande pode degradar planejamento/executacao no banco.
- Preload por usuario evita custo de query massiva com parametros gigantes.

Alternativas consideradas:
- Continuar apenas com `findByUserIdAndGoogleEventIdIn`: mais simples, pior em alto volume.
- Query nativa com tabela temporaria: ganho potencial, maior complexidade operacional.

### 3) Reduzir CPU em normalizacao e parsing

Decisao:
- Adicionar cache por execucao `raw -> normalized` para strings repetidas.
- Substituir regex em hot path por parsing/manual/precompiled pattern quando aplicavel.
- Preservar a mesma semantica de normalizacao e parser atual.

Rationale:
- Regex por evento e por serviço custa CPU relevante em 14k+ itens.
- Cache de normalizacao evita repetir o mesmo trabalho textual.

Alternativas consideradas:
- Cache global compartilhado: mais complexidade e invalidação.
- Não otimizar CPU: latencia continua elevada mesmo com tuning de I/O.

### 4) Persistir apenas quando houver mudanca efetiva

Decisao:
- Implementar detecção de no-op para pular `save` de eventos sem mudança de campos relevantes.
- Evitar recomputar/reescrever associações de serviços quando o conjunto associado já equivale ao atual.

Rationale:
- Em incremental com poucos deltas, grande parte pode ser reprocessada sem mudança real.
- Evita escrita desnecessaria e custo de ORM/DB.

Alternativas consideradas:
- Manter persistencia sempre: simples, mas caro e desnecessario.

### 5) Guardrails de cobertura de dados

Decisao:
- Validar explicitamente que tuning de `maxResults` e `fields` nao reduz conjunto de dados sincronizados.
- Adicionar checks de regressao para contagem total e cenarios de cancelamento/fallback.

Rationale:
- Meta de performance nao pode comprometer integridade de sincronizacao.

## Risks / Trade-offs

- [Campos insuficientes na Google API por `fields`] -> Mitigacao: manter lista minima obrigatoria (`id`, `status`, `summary`, `start`, `end`, tokens de pagina/sync) e testes de regressao.
- [Batch maior gerar pressao de memoria/locks] -> Mitigacao: limites configuraveis, observabilidade por etapa e rollback rapido por config.
- [Cache por execucao crescer demais] -> Mitigacao: escopo por request e descarte ao fim da sincronizacao.
- [Heuristica de no-op errada] -> Mitigacao: comparar apenas campos semanticos relevantes e cobrir com testes de igualdade/alteracao.

## Migration Plan

1. Aplicar tuning de configuracao e instrumentar baseline pós-ajuste.
2. Implementar preload para full sync e cache local de normalizacao.
3. Implementar parser/normalizacao otimizada sem alterar semantica.
4. Implementar no-op detection de persistencia e associacao.
5. Executar benchmark comparativo no dataset de 14.354 eventos.
6. Validar contrato funcional e cobertura de dados.
7. Rollback via configuracao (batch/pages/JDBC flags) e/ou revert de blocos de no-op/preload.

## Open Questions

- A meta de 10s sera validada em ambiente local, homologacao ou ambos?
- O critério de aceite deve usar p95 de varias execuções ou apenas pior caso unico?
- Para full sync inicial, aceitaremos janela de aquecimento maior que incremental?

## Current Implementation Status and Rollback

Implemented in code:
- Config tuning applied in `application.yml` (`GOOGLE_CALENDAR_SYNC_MAX_RESULTS=2500`, `CALENDAR_SYNC_BATCH_SIZE=1000`, `CALENDAR_SYNC_BATCH_CLEAR_ENABLED=true`, `reWriteBatchedInserts=true`).
- Full sync local lookup now uses user preload (avoids giant `IN`).
- Per-sync normalization cache added to avoid repeated normalization work.
- Hot path parsing/normalization refactored to manual/precompiled-like logic without per-call regex compilation.
- No-op detection added to skip persistence when event data did not change.
- Service association rewrite is skipped when the current association is already equivalent.

Functional safety checks completed in automated tests:
- Existing fallback on expired sync token is preserved.
- Existing forbidden/revoked error mapping behavior is preserved.
- Existing response counters contract is preserved in tested scenarios.
- Regression coverage added for parser/normalizer equivalence and no-op/service-equivalent sync scenarios.

Pending validation in target environment:
- Run comparative benchmark for the 14,354-event dataset.
- Confirm `sync_total_ms <= 10000` under agreed environment and timeout conditions.

Rollback procedure by configuration:
1. Set `GOOGLE_CALENDAR_SYNC_MAX_RESULTS` back to previous value (for example `1000`).
2. Set `CALENDAR_SYNC_BATCH_SIZE` back to previous value (for example `200`).
3. Set `CALENDAR_SYNC_BATCH_CLEAR_ENABLED=false`.
4. Set `POSTGRES_REWRITE_BATCHED_INSERTS=false`.
5. Restart API and compare stage logs (`google_fetch_ms`, `db_lookup_ms`, `processing_ms`, `db_write_ms`, `sync_total_ms`).

Code rollback options:
- Revert full-sync preload path in `CalendarSyncService` to prior targeted lookup behavior.
- Revert no-op/service-equivalence guards if any unexpected edge case appears in production.
