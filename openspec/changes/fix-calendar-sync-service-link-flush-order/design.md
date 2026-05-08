## Context

O backend já corrigiu o conflito entre `orphanRemoval` e o bulk delete explícito de `calendar_event_services`, mas a sincronização ainda falha em um cenário específico de produção: o mesmo chunk processa um evento deletado e outro evento persistido que precisa substituir o conjunto de serviços reconhecidos. Hoje `processChunkMutations` atualiza entidades gerenciadas em memória antes de `persistMutations`, e o bloco de deleções executa operações de cleanup que podem provocar flush antes de o replace dos `serviceLinks` acontecer.

O ponto mais sensível é o cleanup de pagamentos, que usa um método `@Modifying(flushAutomatically = true, clearAutomatically = true)`. Quando esse método roda antes do delete canônico dos `serviceLinks`, o provider pode tentar persistir os novos vínculos do evento atualizado enquanto as linhas antigas ainda estão no banco. Isso viola a constraint única `(calendar_event_id, service_id)` e retorna erro 500 no endpoint existente.

## Goals / Non-Goals

**Goals:**
- Garantir que o replace de `calendar_event_services` execute antes de qualquer operação que possa disparar flush da mesma unidade de trabalho.
- Tornar determinística a ordem de persistência quando um chunk mistura `upserts`, `deletions` e cleanups auxiliares.
- Preservar o contrato HTTP atual de `POST /api/calendar/sync` e de `GET /api/calendar/status`.
- Cobrir com testes o cenário real de produção em que o mesmo chunk contém deleção e replace de serviços.

**Non-Goals:**
- Não redesenhar o parser de títulos nem a regra funcional de reconhecimento de serviços.
- Não alterar payloads de API nem introduzir novo endpoint de sincronização.
- Não remover a constraint única de `calendar_event_services`.
- Não reestruturar todo o pipeline de sync para múltiplas transações por evento, exceto se isso for estritamente necessário para estabilizar a ordem.

## Decisions

### 1. Executar o cleanup de replace dos `serviceLinks` antes do bloco de deleções
**Decision:** `persistMutations` deve priorizar a remoção explícita dos `calendar_event_services` que serão substituídos antes de qualquer cleanup de deleções ou exclusão em lote de eventos.

**Why:** O replace de serviços é o estado mais frágil da transação porque já existe alteração pendente em entidades gerenciadas. Fazer o delete canônico dos links antigos primeiro elimina a janela em que um flush antecipado ainda encontra linhas conflitantes no banco.

**Alternatives considered:**
- Manter a ordem atual e confiar apenas em `FlushModeType.COMMIT`: rejeitada porque outras operações ainda podem forçar flush antes do delete dos links.
- Dividir cada tipo de mutação em transações separadas por evento: rejeitada por aumentar complexidade e custo operacional sem necessidade clara.

### 2. Remover ou neutralizar pontos de autoflush no cleanup de deleções
**Decision:** O cleanup de pagamentos e qualquer outra deleção auxiliar usada no mesmo fluxo deve deixar de provocar autoflush implícito antes da fase de replace dos `serviceLinks`.

**Why:** Mesmo com a reordenação, o design precisa impedir que uma query auxiliar reintroduza o mesmo risco em evoluções futuras. O contrato esperado é: primeiro limpar links a substituir, depois executar os demais cleanups com flush explícito e intencional.

**Alternatives considered:**
- Apenas mudar a ordem e manter `flushAutomatically = true`: rejeitada porque a fragilidade continua escondida em um detalhe de repositório.
- Ignorar a exceção e reexecutar o sync: rejeitada porque a falha já interrompe o fluxo real do usuário e mascara inconsistência transacional.

### 3. Formalizar cobertura de regressão para mutações mistas no mesmo chunk
**Decision:** A suíte precisa reproduzir explicitamente um chunk com:
1. um evento persistido entrando em replace de `serviceLinks`;
2. um evento deletado com payments a limpar;
3. a mesma transação concluindo sem `duplicate key` nem erro 500.

**Why:** Os testes atuais validam replace isolado e deleções isoladas, mas não cobrem a composição que dispara o flush antecipado. Sem esse cenário, a regressão volta facilmente.

**Alternatives considered:**
- Confiar apenas em testes de integração do repositório: rejeitada porque o problema emerge da ordem entre múltiplas mutações no serviço de sync.

## Risks / Trade-offs

- [Reordenar `persistMutations` pode alterar pressupostos de limpeza para eventos deletados] -> Mitigação: validar com testes que deleções continuam removendo payments e eventos corretamente após o replace dos `serviceLinks`.
- [Remover autoflush de um repositório pode esconder dependências implícitas em outros fluxos] -> Mitigação: explicitar flushes necessários no serviço e revisar os usos desse repositório fora do sync.
- [Mais fases explícitas de persistência aumentam a verbosidade do serviço] -> Mitigação: manter responsabilidades nomeadas e cobertas por testes focados em ordem de execução.

## Migration Plan

1. Ajustar a ordem de persistência no `CalendarSyncService` para executar primeiro o replace canônico dos `serviceLinks`.
2. Revisar o repositório de cleanup de payments para remover o flush implícito precoce ou substituí-lo por flush explícito no ponto correto.
3. Adicionar testes cobrindo o caso misto de replace + deletion no mesmo chunk.
4. Executar a suíte focada de sync/reprocessamento e repetir o smoke test com a conta problemática após deploy.

**Rollback strategy:**
- Reverter o deploy se a mudança introduzir regressão em deleções de eventos ou no cleanup de payments.
- Como a correção é de ordem transacional, o rollback não exige migração de dados, apenas retorno ao comportamento anterior.

## Open Questions

- Existe algum outro repositório ou listener de sync que ainda possa forçar flush antecipado antes da fase de replace dos `serviceLinks`?
- Vale consolidar os cleanups auxiliares de sync atrás de um contrato único de persistência para reduzir novas regressões de ordem?
