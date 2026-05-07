## Context

O `POST /api/calendar/sync` processa grandes volumes e executa limpeza/reconciliacao com delecao em lote. Em producao, o fluxo falha com `TransactionRequiredException` (`No EntityManager with actual transaction available for current thread`) durante `deleteInBulkByCalendarEventIdIn`/`flush`, o que indica operacao de escrita JPA fora de transacao ativa. A falha quebra o sync apos parte do processamento e retorna erro 500 para o cliente.

## Goals / Non-Goals

**Goals:**
- Garantir que todas as operacoes de escrita/flush do sync rodem em fronteira transacional valida.
- Eliminar `InvalidDataAccessApiUsageException`/`TransactionRequiredException` no caminho de cleanup/delecao.
- Preservar contrato do endpoint (`200` com `created/updated/deleted` em sucesso; mapeamentos de erro existentes).
- Manter compatibilidade para sync com e sem `startDate`.

**Non-Goals:**
- Redesenhar algoritmo de matching/reconciliacao de eventos.
- Otimizacoes de latencia alem do necessario para a correcao transacional.
- Alterar payloads do endpoint ou comportamento de autenticacao/autorizacao.

## Decisions

1. Aplicar fronteira transacional no fluxo de persistencia do sync.
- Decisao: executar caminho de mutacao (upsert/delecao/cleanup e flush) dentro de metodo transacional unico na camada de servico.
- Racional: garante `EntityManager` transacional disponivel para metodos `@Modifying` e `flush`.
- Alternativa considerada: anotar apenas metodos de repositorio. Rejeitada porque nao garante atomicidade do fluxo completo de mutacoes.

2. Manter lock por usuario fora da mudanca de contrato.
- Decisao: preservar `ReentrantLock` por usuario como controle de concorrencia e combinar com transacao por execucao de sync.
- Racional: evita corridas entre syncs concorrentes sem alterar API.
- Alternativa considerada: remover lock e confiar apenas na transacao. Rejeitada por risco de contencao/logica concorrente no mesmo usuario.

3. Garantir consistencia de estado de sync em falha.
- Decisao: manter persistencia de estado `SYNC_FAILED` para falhas internas e nao mascarar erro de aplicacao como `UNAUTHORIZED`.
- Racional: melhora observabilidade e evita diagnostico incorreto no app cliente.
- Alternativa considerada: engolir excecao e responder sucesso parcial. Rejeitada por violar confiabilidade do contrato.

4. Cobrir regressao com testes focados em transacao no cleanup.
- Decisao: adicionar/ajustar testes para cenarios com delecao e flush no sync inicial/full e com `startDate`.
- Racional: previne retorno do erro em refactors futuros.

## Risks / Trade-offs

- [Risco] Transacao longa em contas com muitos eventos pode aumentar tempo de lock de banco. → Mitigacao: manter batch de escrita e monitorar `db_write_ms`/tempo total; ajustar chunk/batch se necessario.
- [Risco] Mudanca de fronteira transacional pode alterar ordem observavel de flush. → Mitigacao: validar contrato funcional existente (contadores `created/updated/deleted` e reconciliacao).
- [Risco] Excecoes internas continuarem sem classificacao clara. → Mitigacao: manter handler global de runtime e logs estruturados de sync.

## Migration Plan

1. Implementar ajuste transacional no backend de sync.
2. Executar suites de sync e seguranca relacionadas ao endpoint.
3. Publicar em ambiente de staging com conta de alto volume.
4. Validar ausencia de `TransactionRequiredException` e latencia aceitavel.
5. Promover para producao.

Rollback:
- Reverter commit da correcao transacional e redeploy.
- Confirmar retorno ao comportamento anterior (com monitoramento de erro conhecido).

## Open Questions

- O metodo transacional deve envolver toda a rotina de sync (incluindo fetch Google) ou apenas fase de mutacoes em banco?
- Precisamos segmentar mutacoes em transacoes menores por chunk para reduzir duracao de lock em bases muito grandes?
