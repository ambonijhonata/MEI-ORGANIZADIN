## Context

O fluxo atual de `POST /api/calendar/sync` busca eventos do Google fora de transacao, monta lookups com entidades JPA recuperadas por queries sem `JOIN FETCH` das associacoes relevantes e depois decide mutacoes antes de entrar na fase de persistencia. Quando um `CalendarEvent` existente precisa atualizar servicos, `associateServices` chama `serviceLinks.clear()`. Como `serviceLinks` e `LAZY` e o evento ja nao esta preso a uma sessao ativa, o Hibernate tenta inicializar a colecao detached e lanca `LazyInitializationException`.

O sistema tambem usa `spring.jpa.open-in-view=false`, entao o backend nao pode depender de sessao estendida para mascarar esse tipo de acesso. Ao mesmo tempo, envolver toda a rotina de sync em uma transacao unica seria arriscado porque incluiria chamada remota ao Google, ampliando tempo de lock e custo de rollback.

## Goals / Non-Goals

**Goals:**
- Eliminar a causa raiz do `LazyInitializationException` em mutacoes de `serviceLinks` durante o sync.
- Garantir que qualquer mutacao de entidade/colecao JPA no sync ocorra com entidade `managed` em transacao ativa.
- Manter a estrategia de `open-in-view=false`.
- Preservar contrato do endpoint e semantica dos contadores `created`, `updated` e `deleted`.
- Manter transacoes curtas, sem incluir a chamada externa ao Google.

**Non-Goals:**
- Mudar o modelo de dominio de `CalendarEvent` ou substituir `serviceLinks` por estrutura nao JPA.
- Tornar todas as relacoes `EAGER` globalmente.
- Reescrever o algoritmo de parsing/matching de titulo.
- Resolver neste change warnings independentes como serializacao de `PageImpl`.

## Decisions

1. Separar o sync em fases com fronteira transacional por chunk de mutacao.
- Decisao: manter `fetchEvents(...)` fora de transacao e executar, para cada chunk, uma etapa transacional que faz lookup dos eventos existentes daquele chunk, processa diferencas e persiste mutacoes.
- Racional: evita transacao longa com I/O externo e garante que as entidades usadas em `associateServices` estejam `managed` no momento da mutacao.
- Alternativas consideradas:
- Anotar `synchronize` inteiro com `@Transactional`: rejeitada por incluir chamada ao Google e aumentar duracao de lock/rollback.
- Continuar processando fora de transacao e apenas fazer `merge/saveAll` no fim: rejeitada porque permite novas quebras de lazy ao tocar colecoes detached.

2. Recarregar explicitamente eventos existentes com associacoes necessarias antes de mutar.
- Decisao: usar metodos de repositorio dedicados por chunk (`findWithAssociationsByUserIdAndGoogleEventIdIn` ou equivalente) para trazer `serviceLinks` e referencias necessarias dos eventos que podem ser atualizados.
- Racional: `serviceLinks.clear()` em colecao inicializada e entidade managed e seguro para dirty checking e orphan removal.
- Alternativas consideradas:
- Tornar `serviceLinks` `EAGER`: rejeitada por custo permanente em leituras nao relacionadas.
- Habilitar `open-in-view`: rejeitada porque mascara o problema e amplia acoplamento da web layer com persistencia.

3. Tratar entidades novas e existentes de maneira diferente no hot path.
- Decisao: eventos novos continuam sendo montados em memoria; eventos existentes so passam por mutacao dentro da transacao/chunk com lookup managed.
- Racional: reduz carga de banco sem abrir mao da seguranca de sessao para updates.

4. Blindar o contrato com testes de reproducao e regressao.
- Decisao: adicionar testes de integracao/servico cobrindo:
- sync incremental com evento existente e mudanca real de servicos;
- sync com `startDate` no mesmo caminho seguro;
- full/incremental delete+flush mantendo integridade transacional;
- ausencia de `LazyInitializationException` e preservacao do status `FAILED` em falhas internas reais.
- Racional: o erro de producao aconteceu num caminho especifico que precisa virar contrato automatizado.

## Proposed Flow

```text
1. synchronize()
   -> lock por usuario
   -> fetch Google fora de transacao
   -> quebrar eventos em chunks
2. para cada chunk
   -> abrir transacao curta
   -> buscar existing events do chunk com associacoes necessarias
   -> montar diffs usando entidades managed
   -> chamar associateServices / clearServiceAssociation apenas dentro da transacao
   -> persistir upserts/deletes do chunk
3. apos chunks
   -> aplicar reconciliacao final e atualizar SyncState
```

## Risks / Trade-offs

- [Risco] Mais round-trips ao banco por chunk ao recarregar eventos com associacoes. -> Mitigacao: limitar fetch ao conjunto de `googleEventId` do chunk e medir `db_lookup_ms`/`processing_ms`.
- [Risco] Implementacao parcial deixar alguns caminhos ainda usando entidades detached. -> Mitigacao: centralizar a mutacao em um unico metodo transacional de chunk e revisar todos os caminhos (`incremental`, `full`, `startDate`).
- [Risco] `JOIN FETCH` em chunks muito grandes pode inflar memoria. -> Mitigacao: manter batch/chunk size configuravel e nao usar eager global.
- [Risco] Novos acessos lazy em outras relacoes surgirem no futuro. -> Mitigacao: testes de regressao com `open-in-view=false` e diretriz de nao mutar colecao lazy fora de metodo transacional managed.

## Migration Plan

1. Introduzir metodo transacional por chunk no `CalendarSyncService`.
2. Migrar o lookup de eventos existentes para consultas com associacoes necessarias dentro desse metodo.
3. Ajustar reconciliacao/delecao para o mesmo modelo de entidade managed e transacao curta.
4. Executar suites de sync cobrindo incremental/full/startDate.
5. Validar em staging com uma conta que tenha multi-servicos e eventos existentes.
6. Monitorar logs do sync e endpoint apos deploy.

Rollback:
- Reverter a refatoracao do fluxo por chunk.
- Restaurar implementacao anterior apenas se acompanhada de mitigacao operacional temporaria, porque o erro atual continuaria reproduzivel.

## Open Questions

- A reconciliacao final deve ocorrer no mesmo metodo transacional por chunk ou em transacao dedicada apos consolidar IDs afetados?
- Vale introduzir um DTO intermediario de diff para desacoplar completamente decisao de update do estado de entidade JPA no futuro?
