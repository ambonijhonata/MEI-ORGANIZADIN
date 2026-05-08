## Context

Depois da change de enriquecimento de agendamentos sincronizados, o fluxo de sync passou a exercitar com mais frequencia o caminho de `replace` dos `serviceLinks` em eventos ja persistidos. Em producao, isso revelou dois erros sequenciais no mesmo ponto da persistencia:

- primeiro, violacao da constraint unica `(calendar_event_id, service_id)` porque o Hibernate tentava inserir o novo link antes da remocao efetiva do antigo;
- depois, apos introduzir o bulk delete com `FlushModeType.COMMIT`, `ObjectOptimisticLockingFailureException` porque a mesma linha era removida uma vez pelo repositorio e outra vez pelo `orphanRemoval` da colecao JPA.

O problema central nao e mais o parser nem a logica de reconhecimento. O defeito esta no contrato de persistencia do replace de links de servico: o codigo hoje mistura remocao implicita por ciclo de vida JPA com remocao explicita por repositorio para o mesmo conjunto de filhos.

## Goals / Non-Goals

**Goals:**
- Garantir que a substituicao de servicos de um evento persistido use um unico caminho canonico de remocao.
- Eliminar tanto a violacao de unicidade quanto o delete duplicado que gera `StaleStateException`.
- Preservar a semantica separada entre `replace` e `enrich`.
- Manter a sincronizacao repetida da conta problematica estavel apos o deploy.
- Cobrir o fluxo real com testes de regressao de sync e reprocessamento.

**Non-Goals:**
- Nao redesenhar o parser de titulos ou a regra funcional de reconhecimento de servicos.
- Nao introduzir full resync do Google Calendar.
- Nao alterar contratos de API externos ou payloads de resposta.
- Nao trocar o modelo historico de snapshots alem do necessario para estabilizar o replace.

## Decisions

### 1. Tornar o replace de eventos persistidos explicitamente dirigido pelo repositorio
**Decision:** O caminho de `replace` para eventos ja persistidos passara a remover links antigos apenas pela estrategia explicita do repositorio antes da insercao do novo conjunto canonico.

**Why:** O bulk delete oferece ordem deterministica para o replace:
1. remover os links antigos do evento;
2. garantir que a persistencia antiga foi encerrada;
3. inserir o novo conjunto.

Isso evita depender da ordem interna do flush do Hibernate para reconciliar delete e insert na mesma colecao.

**Alternatives considered:**
- Manter apenas `clear()+add()` com `orphanRemoval`: rejeitada porque foi a origem da violacao de unicidade no caso real.
- Manter bulk delete e `orphanRemoval` juntos com novos ajustes de flush: rejeitada porque a dupla responsabilidade continua e so troca o tipo de falha.

### 2. Remover a remocao implicita concorrente do mesmo conjunto de filhos
**Decision:** O modelo deve deixar de agendar deletes implicitos dos mesmos `serviceLinks` que ja serao removidos pelo repositorio no fluxo de replace.

**Why:** O erro atual e causado por dois agentes tentando apagar a mesma linha:
- o repositorio remove em lote por `calendar_event_id`;
- a entidade gerenciada ainda entende que os filhos removidos da colecao sao orfaos e agenda `delete where id=?`.

O plano de correcao precisa fazer o sistema escolher um unico dono da remocao nesse fluxo.

**Alternatives considered:**
- Ignorar o `StaleStateException`: rejeitada porque mascara corrupcao potencial e deixa a sync falhando.
- Desativar todo controle de erro de row count no Hibernate: rejeitada por ser ampla demais e insegura.

### 3. Separar semanticamente `replace` de `enrich`
**Decision:** O backend mantera dois caminhos distintos:
- `replace`: troca o conjunto inteiro de associacoes do evento persistido e usa o mecanismo explicito de remocao;
- `enrich`: apenas adiciona servicos faltantes a um evento existente, preservando os links e snapshots ja validos.

**Why:** O bug aconteceu num fluxo de replace, enquanto a change anterior introduziu um fluxo de enrich. Misturar novamente os dois comportamentos no mesmo metodo aumenta o risco de regressao e de reescrita indevida de snapshots.

**Alternatives considered:**
- Reutilizar um unico metodo com flags internas pouco visiveis: rejeitada por deixar a manutencao mais fragil.

### 4. Garantir idempotencia do sync posterior
**Decision:** A sincronizacao posterior deve tratar o conjunto de links persistido como estado canonico e nao falhar quando precisar substituir um conjunto equivalente ou enriquecido anteriormente.

**Why:** O problema reportado pelo usuario aparece exatamente ao reexecutar a sincronizacao na conta problematica. A correcao so esta completa se o segundo sync tambem terminar sem duplicidade, sem stale delete e sem inflar o resultado financeiro.

### 5. Cobrir a falha real com testes de integracao do fluxo de persistencia
**Decision:** Os testes devem reproduzir a sequencia observada:
- evento persistido com links antigos;
- novo sync ou reprocessamento entra no caminho de replace;
- remocao e reinsercao acontecem na mesma transacao;
- o fluxo conclui sem `duplicate key` e sem `delete where id=?` retornando zero linhas.

**Why:** O defeito nao aparece em cenarios puramente unitarios da entidade; ele depende da interacao entre estado gerenciado, bulk delete, flush e save.

## Risks / Trade-offs

- [Remover `orphanRemoval` ou mudar seu papel pode afetar outros pontos de exclusao] -> Mitigacao: mapear todas as rotas que removem `serviceLinks` e garantir que elas usem o mesmo contrato canonico.
- [Replace explicito pode recriar ids tecnicos dos links] -> Mitigacao: os ids de `calendar_event_services` nao sao contrato externo; o que precisa permanecer estavel e o snapshot semantico.
- [Separar `replace` e `enrich` em caminhos distintos aumenta o numero de metodos] -> Mitigacao: a clareza do contrato reduz regressao e facilita testes.

## Migration Plan

1. Ajustar o contrato de persistencia de `calendar_event_services` para que o replace de evento persistido tenha uma unica estrategia de remocao.
2. Revisar o agregado `CalendarEvent` para evitar limpeza da colecao que agende deletes concorrentes ao bulk delete.
3. Garantir que `CalendarSyncService` e `CalendarEventReprocessor` usem o caminho correto para `replace` e `enrich`.
4. Adicionar testes cobrindo a conta problematica em termos de sequencia funcional: sync, enriquecimento previo, sync posterior.
5. Validar localmente com a suite focada e repetir o smoke test na conta afetada apos deploy.

**Rollback strategy:**
- Reverter o deploy se a nova estrategia causar regressao funcional.
- Manter os dados ja enriquecidos, pois a correcao e de estabilidade transacional e nao exige rollback de conteudo.

## Open Questions

- Existe algum outro fluxo alem de sync/reprocessamento que hoje dependa de `orphanRemoval` para apagar `serviceLinks`?
- Vale introduzir um teste de repositorio dedicado que valide explicitamente a ordem delete-then-insert para `calendar_event_services`?
