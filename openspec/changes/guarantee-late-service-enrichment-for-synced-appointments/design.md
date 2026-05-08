## Context

A change `enrich-synced-appointments-with-late-services` ja introduziu o enriquecimento assÃ­ncrono de agendamentos sincronizados quando o catalogo ganha servicos novos ou renomeados. Isso resolveu o caso ideal em que o gatilho do catalogo roda com sucesso logo apos o cadastro.

O problema remanescente e estrutural:
- a sync incremental do Google so revisita eventos que voltam no delta;
- uma nova sync manual nao reavalia todo o historico local sem mudanca no Google;
- o enrich atual depende de uma chamada best-effort no momento do `createService` ou rename;
- casos antigos ja parcialmente sincronizados antes do deploy, falhas do job async, ou retries perdidos podem deixar eventos incompletos indefinidamente.

Por isso ainda e possivel observar cenarios como:
- `sobrancelha + buco` permanecer apenas com `sobrancelha`;
- `sobrancelha + tintura` permanecer apenas com `sobrancelha`;
- outro evento ficar correto porque aquele servico especifico ja existia no momento da sync original.

O dado necessario para corrigir esses casos ja esta localmente salvo:
- titulo persistido do evento;
- catalogo atual do usuario.

Logo, pedir um novo sync completo ao Google apos cada cadastro de servico nao e o melhor mecanismo de correcao.

## Goals / Non-Goals

**Goals:**
- Garantir que agendamentos sincronizados sejam eventualmente reenriquecidos quando o catalogo do usuario muda.
- Corrigir tanto casos novos quanto eventos legados parcialmente sincronizados antes do deploy.
- Fazer a sync manual/automatica consumir pendencias locais de enriquecimento, mesmo sem delta do Google.
- Manter o enrich assÃ­ncrono por evento de catalogo como fast path, mas adicionar retry persistido e backfill.
- Preservar a semantica atual: enriquecimento local usa o titulo salvo e o catalogo atual, sem full resync com Google.

**Non-Goals:**
- Nao transformar cadastro de servico em full Google resync.
- Nao alterar parser de titulo, normalizacao ou a regra de snapshots historicos alem do necessario.
- Nao reprecificar retroativamente eventos so porque o valor atual do servico mudou.
- Nao introduzir dependencia externa nova para fila, scheduler distribuido ou lock externo.

## Decisions

### 1. Persistir uma pendencia de enriquecimento por usuario
**Decision:** Introduzir estado persistido por usuario para representar que o catalogo mudou de forma que pode enriquecer eventos sincronizados antigos.

**Recommended shape:**
- guardar esse estado em `SyncState` ou estrutura sync-adjacent equivalente;
- usar um modelo monotÃ´nico, por exemplo:
  - `catalog_enrichment_revision_requested`
  - `catalog_enrichment_revision_applied`
  ou
  - `catalog_enrichment_pending` + `catalog_enrichment_requested_at`

**Why:** O problema do modelo atual e depender de uma unica chamada assÃ­ncrona no instante do cadastro. Persistir a pendencia torna o reenriquecimento:
- recuperavel apos falha;
- reexecutavel em sync posterior;
- aplicavel a backfill legado.

**Alternatives considered:**
- depender apenas do `@Async` atual: rejeitada porque nao oferece garantia eventual.
- gravar a pendencia por evento individual: rejeitada neste momento por custo e complexidade desnecessarios; o titulo salvo permite recalcular em lote por usuario.

### 2. Tratar o cadastro/rename de servico como sinal local, nao como gatilho de Google sync
**Decision:** Ao criar um servico ou alterar sua descricao normalizada:
1. marcar a pendencia/revisao de enriquecimento do usuario;
2. disparar o enrich assÃ­ncrono atual como tentativa imediata;
3. limpar a pendencia apenas apos sucesso do enriquecimento completo.

**Why:** Isso preserva a boa UX do fast path sem perder confiabilidade quando a execucao assÃ­ncrona falhar ou nao cobrir casos legados.

**Alternatives considered:**
- disparar sync incremental no cadastro: rejeitada porque nao revisita eventos sem delta do Google.
- disparar full resync no cadastro: rejeitada por custo alto, acoplamento com Google e risco operacional.

### 3. Fazer a sync consumir pendencias locais antes de concluir
**Decision:** `CalendarSyncService` deve verificar, no inicio ou antes do encerramento bem-sucedido, se existe pendencia de enriquecimento local para o usuario. Se existir, deve executar o enriquecimento local dentro do fluxo protegido por lock e marcar a pendencia como aplicada somente apos sucesso.

**Why:** A sync manual ja e a acao que o usuario tenta quando percebe dados desatualizados. Integrar a pendencia a esse fluxo garante que:
- a sync manual realmente possa corrigir eventos antigos;
- o sistema nao dependa de delta do Google para reparar associacoes locais;
- retries acontecam naturalmente mesmo para casos legados.

**Alternatives considered:**
- manter a sync e o enrich totalmente separados: rejeitada porque perpetua o problema reportado.
- executar sempre um full enrich em toda sync de todo usuario: rejeitada por custo evitavel; deve acontecer apenas quando houver pendencia.

### 4. Executar backfill inicial para usuarios com historico Google-backed
**Decision:** A entrega deve incluir um backfill inicial que marque usuarios com eventos sincronizados como candidatos a reenriquecimento pelo menos uma vez apos o deploy.

**Recommended shape:**
- migracao de dados que inicializa a pendencia/revisao para usuarios com `calendar_events.google_event_id` preenchido;
- alternativa aceitavel: job de bootstrap controlado por deploy que marca a pendencia para todos os usuarios com eventos Google-backed.

**Why:** Sem backfill, a nova garantia so valeria para servicos cadastrados apos este deploy. Isso nao corrige os casos ja legados, como o `userId=4`.

**Alternatives considered:**
- esperar que novos cadastros de servico acionem novamente o enrich: rejeitada porque nao cobre usuarios cujo catalogo ja esta completo.
- instruir operacao manual por usuario afetado: rejeitada porque nao e generica e nao cobre casos nao reportados.

### 5. Manter enriquecimento local baseado em titulo salvo
**Decision:** O reprocessamento de pendencia deve continuar usando:
- `CalendarEvent.title` persistido;
- parser de titulo;
- catalogo atual do usuario;
- sem chamar Google para reobter o evento.

**Why:** O defeito e local e o estado necessario ja existe no banco. Isso torna a correcao mais barata, deterministica e desacoplada de disponibilidade/latencia do Google.

### 6. Preservar separacao entre `replace` e `enrich`
**Decision:** O backfill/retry proposto sempre deve usar o caminho de `enrich` para eventos ja parcialmente corretos, preservando snapshots validos e adicionando apenas associacoes faltantes. `replace` continua reservado para quando a sync recalcula o conjunto canonico a partir de uma mudanca real do evento.

**Why:** Isso evita reescrever historico e reaproveita a semantica ja estabelecida nas correcoes anteriores.

## Risks / Trade-offs

- [Varredura de historico por usuario pode aumentar custo da sync quando houver pendencia] -> Mitigacao: executar apenas quando o marcador persistido indicar necessidade e limpar o marcador apos sucesso.
- [Backfill inicial pode disparar muito reenriquecimento apos deploy] -> Mitigacao: limitar a usuarios com eventos Google-backed e monitorar execucao por lote/lock de usuario.
- [Fast path async e retry via sync podem correr juntos] -> Mitigacao: ambos devem permanecer sob o mesmo `UserScopedExecutionLock` e compartilhar a mesma regra de conclusao da pendencia.
- [Modelo booleano simples pode perder eventos de catalogo ocorridos durante enriquecimento] -> Mitigacao: preferir revisao monotÃ´nica ou requested/applied version em vez de booleano efemero.

## Migration Plan

1. Adicionar campos persistidos para revisao ou pendencia de enriquecimento local por usuario.
2. Preencher esses campos para usuarios com eventos Google-backed como backfill inicial.
3. Atualizar `ServiceCatalogService` para marcar revisao pendente em create e rename antes de disparar o fast path async.
4. Atualizar `CalendarEventReprocessor` para marcar sucesso apenas quando concluir a varredura completa do usuario.
5. Atualizar `CalendarSyncService` para consumir a pendencia local durante sync manual/automatica mesmo sem delta do Google.
6. Validar com testes cobrindo:
   - caso legado ja existente antes do deploy;
   - falha do fast path seguida de correção na sync seguinte;
   - estabilidade do agendamento apos enriquecimento e sync posterior.

**Rollback strategy:**
- Se o codigo falhar, reverter o deploy mantendo a pendencia persistida; ela e segura e pode ser reaproveitada no redeploy correto.
- Se o backfill gerar carga acima do esperado, pausar o consumo automatico da pendencia e processar por lote, sem perder o marcador ja gravado.

## Open Questions

- A pendencia deve ser armazenada em `SyncState` ou em outro agregado mais claramente ligado ao catalogo do usuario?
- Queremos um endpoint/admin action temporario para forcar reenriquecimento de um `userId` especifico durante suporte operacional?
