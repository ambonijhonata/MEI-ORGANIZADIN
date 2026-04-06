## Context

O endpoint `POST /api/calendar/sync` processa sincronizacao incremental/full do Google Calendar e hoje executa processamento evento a evento, incluindo consultas e persistencia repetidas por item. Em contas com alto volume de eventos, esse desenho eleva a latencia total e gera timeout no cliente Android, que passa a exibir aviso de sincronizacao parcial.

O escopo desta change e exclusivamente otimizar a execucao interna do endpoint de sync, mantendo o comportamento funcional e o contrato HTTP existentes. Isso inclui preservar respostas de sucesso/erro, contadores `created/updated/deleted`, semantica de `syncToken` e fallback para full resync em expiracao de token.

Stakeholders:
- Usuario final do app (tempo de sincronizacao menor e menos timeouts).
- API backend (estabilidade e custo operacional previsivel em alto volume).
- Time de suporte/produto (menos erros intermitentes de timeout e melhor diagnostico).

Restricoes:
- Sem alteracao de contrato dos endpoints atuais.
- Sem mudancas funcionais em outros endpoints.
- Sem mudanca de regra de negocio para identificacao de cliente/servico e snapshots financeiros.

## Goals / Non-Goals

**Goals:**
- Reduzir significativamente o tempo de execucao do `POST /api/calendar/sync` em contas de alto volume.
- Eliminar gargalos de N+1 e persistencia item a item no processamento de eventos.
- Tornar o custo de processamento mais linear e previsivel por lote de eventos.
- Melhorar observabilidade da sincronizacao por etapa (Google fetch, processamento, persistencia).
- Preservar integralmente comportamento funcional e contrato da API.

**Non-Goals:**
- Criar novos endpoints de sync.
- Alterar payloads de resposta/erro do endpoint.
- Redesenhar fluxo de UI no app Android.
- Mudar regras de matching de servico/cliente.
- Implementar push/webhook de sincronizacao em tempo real.

## Decisions

### 1) Preservar contrato e comportamento como invariantes

Decisao:
- Definir como inegociavel a preservacao dos comportamentos atuais de `POST /api/calendar/sync`:
- mesmos codigos HTTP e shape de resposta;
- mesma semantica de contagem `created/updated/deleted`;
- mesmo tratamento de `syncToken` e fallback de full resync no 410.

Rationale:
- O app Android ja depende desse contrato e do fluxo de fallback atual.
- Reduz risco de regressao funcional ao limitar a change a desempenho interno.

Alternativas consideradas:
- Ajustar endpoint para async/202: maior ganho potencial de UX, mas altera contrato (fora de escopo).
- Criar endpoint paralelo de sync otimizado: adiciona complexidade de compatibilidade e rollout.

### 2) Processamento orientado a lote para remover N+1

Decisao:
- Substituir consultas repetitivas por evento por carregamento em lote por usuario no inicio da sincronizacao:
- mapa de eventos locais por `googleEventId`;
- mapa de clientes por `normalizedName`;
- mapa de servicos por `normalizedDescription`.
- Aplicar persistencia em lote (`saveAll`/batch do Hibernate) com flushing controlado por chunk.

Rationale:
- O custo dominante atual vem de round-trips repetidos de banco por evento e por servico associado.
- Mapas em memoria por execucao reduzem I/O e mantem regra de negocio igual.

Alternativas consideradas:
- Apenas adicionar indices: melhora parte de lookup, mas nao elimina N+1.
- Paralelizar processamento por evento: aumenta risco de contencao/concorrencia sem resolver custo base de I/O.

### 3) Otimizacao de leitura na Google Calendar API sem alterar semantica

Decisao:
- Manter logica de paginação e `syncToken`, mas reduzir overhead por chamada:
- usar `maxResults` adequado para diminuir numero de paginas;
- usar `fields` para buscar somente dados necessarios ao processamento local;
- manter tratamento de token expirado (410) com full resync.

Rationale:
- Parte da latencia total vem da soma de chamadas externas e payloads maiores que o necessario.
- Essa melhoria e compativel com o contrato atual e nao muda comportamento de negocio.

Alternativas consideradas:
- Alterar para estratégia de sync diferente de `syncToken`: alto risco funcional e fora do escopo.
- Cache compartilhado entre execucoes: maior complexidade de invalidacao.

### 4) Controle de concorrencia por usuario durante sync

Decisao:
- Serializar execucoes concorrentes de sync para o mesmo `userId` (lock leve por usuario na camada de servico), evitando processamento duplicado simultaneo.

Rationale:
- Navegacao rapida no app pode disparar multiplos syncs quase simultaneos para o mesmo usuario.
- Concorrencia sem coordenação aumenta custo total e chance de timeout.

Alternativas consideradas:
- Nenhum controle de concorrencia: simples, mas desperdiça recursos em pico.
- Lock global da aplicacao: reduz throughput entre usuarios distintos.

### 5) Observabilidade obrigatoria por etapa

Decisao:
- Instrumentar logs/metricas com duracao e volume por etapas:
- `google_fetch_ms`, `events_received`;
- `db_lookup_ms`, `db_write_ms`;
- `sync_total_ms`, `created`, `updated`, `deleted`;
- flags de full resync e retries.

Rationale:
- Sem telemetria por etapa, gargalos voltam a ocorrer sem diagnostico rapido.
- Permite validar ganhos e detectar regressao apos rollout.

Alternativas consideradas:
- Medir apenas tempo total: insuficiente para localizar gargalo real.

## Risks / Trade-offs

- [Aumento de uso de memoria por caches em lote] -> Mitigacao: processar por chunks e limitar tamanho de estruturas em memoria.
- [Lock por usuario pode aumentar fila em cliques sequenciais] -> Mitigacao: lock somente por usuario, preservando paralelismo entre usuarios diferentes.
- [Batch de persistencia pode alterar padrao de flush e pressionar transacao] -> Mitigacao: configurar tamanho de lote, flush/clear controlados e testes com massa alta.
- [Tuning especifico de ambiente pode variar entre dev/homolog/prod] -> Mitigacao: parametros configuraveis via properties e validacao com metricas reais.

## Migration Plan

1. Implementar instrumentacao baseline no fluxo atual de sync para registrar tempos por etapa.
2. Introduzir lookup em lote (eventos/clientes/servicos) com cobertura de testes de regressao funcional.
3. Introduzir persistencia em lote com tuning configuravel e testes de volume.
4. Otimizar cliente Google Calendar (`maxResults`, `fields`) mantendo semantica existente.
5. Adicionar controle de concorrencia por usuario e validar impacto em carga concorrente.
6. Rollout gradual com comparacao de metrica antes/depois (`p50/p95/p99` de `sync_total_ms`).
7. Rollback: feature flags/propriedades para desativar batch e lock, retornando ao modo anterior sem mudar contrato.

## Open Questions

- Quais metas oficiais de desempenho serao adotadas para aceite (ex.: p95 com 15k eventos)?
- O lock por usuario deve bloquear aguardando conclusao ou retornar reutilizacao do resultado da execucao em andamento?
- Qual estrategia de observabilidade sera padronizada (somente logs estruturados ou tambem metricas Prometheus)?
