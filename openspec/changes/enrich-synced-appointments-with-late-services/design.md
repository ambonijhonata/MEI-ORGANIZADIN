## Context

O backend ja suporta associacao parcial de servicos durante a sincronizacao: se o titulo `fulano - sobrancelha + buco + tintura` encontra apenas `sobrancelha` no catalogo, o evento fica identificado com esse subconjunto. Depois disso, o cadastro de novos servicos dispara apenas `reprocessUnidentifiedEvents(userId)`, o que exclui justamente os eventos parcialmente identificados.

Isso cria um gap funcional:
- o evento salvo continua com `identified=true`, entao nao entra mais no reprocessamento atual;
- a sincronizacao incremental so revisita eventos devolvidos pelo Google como alterados, entao uma nova sync manual nao recompone o conjunto de servicos se o evento nao mudou no Google;
- o metodo atual de reassociacao recria todos os links e snapshots do evento, o que seria inadequado para um fluxo de enriquecimento que deve preservar snapshots historicos ja corretos.

## Goals / Non-Goals

**Goals:**
- Garantir que agendamentos ja sincronizados sejam reavaliados quando o catalogo do usuario ganhar servicos que antes estavam ausentes.
- Permitir que eventos parcialmente identificados recebam os servicos faltantes sem depender de nova alteracao no Google Calendar.
- Preservar snapshots historicos e links ja corretos enquanto adiciona apenas as novas associacoes reconhecidas.
- Manter o comportamento idempotente e serializado por usuario para evitar duplicidade ou regressao em sync posterior.
- Cobrir o fluxo real com testes de regressao.

**Non-Goals:**
- Nao reprecificar snapshots historicos quando apenas o valor atual do servico muda.
- Nao redesenhar a estrategia de sync incremental com Google para buscar todos os eventos novamente a cada cadastro.
- Nao alterar o parser de titulos ou a semantica de identificacao parcial ja aceita.
- Nao introduzir nova tabela ou migracao de banco se o enriquecimento puder ser resolvido com o modelo atual.

## Decisions

### 1. Separar reprocessamento de nao identificados de enriquecimento de eventos sincronizados
**Decision:** Introduzir um fluxo explicito de enriquecimento pos-catalogo que opere sobre eventos sincronizados potencialmente incompletos, em vez de limitar o trabalho a `identified=false`.

**Why:** O comportamento desejado nao e "tentar identificar eventos zerados", e sim "recalcular o conjunto reconhecivel de servicos do titulo salvo com o catalogo atual". Eventos parcialmente identificados ja nao entram no fluxo atual, entao a regra de selecao precisa mudar.

**Alternatives considered:**
- Forcar full resync apos cada cadastro: rejeitada por custo alto e por depender desnecessariamente do Google.
- Depender apenas de nova sync incremental manual: rejeitada porque eventos sem delta no Google nao sao revisitados.
- Marcar eventos parciais como `identified=false`: rejeitada porque quebra o significado atual de identificacao e impacta relatorios.

### 2. Adotar semantica de `enrich` em vez de `replace` para o fluxo pos-catalogo
**Decision:** O enriquecimento deve comparar os servicos atualmente reconhecidos no titulo com as associacoes persistidas e adicionar apenas os servicos faltantes. O fluxo nao deve reconstruir snapshots de servicos ja associados corretamente.

**Why:** `associateServices(...)` recria links e recalcula snapshots a partir do catalogo atual. Isso serve para syncs que refletem o estado atual do evento do Google, mas nao para um fluxo que precisa preservar o historico financeiro das associacoes existentes.

**Alternatives considered:**
- Reutilizar `associateServices(...)` sem diferenciacao: rejeitada porque reescreve snapshots antigos.
- Congelar totalmente eventos identificados e nunca enriquecelos: rejeitada porque perpetua o bug reportado.
- Criar uma tabela separada de tokens ausentes: rejeitada por aumentar complexidade sem necessidade imediata.

### 3. Selecionar candidatos por heuristica orientada ao titulo salvo
**Decision:** O enriquecimento deve percorrer eventos sincronizados do usuario e reparsear o `title` persistido, priorizando eventos que ainda possam ganhar associacoes novas. A decisao de mutar cada evento deve ser feita em memoria comparando:
- tokens de servico extraidos do titulo;
- conjunto de servicos atualmente reconhecivel com o catalogo;
- conjunto de servicos ja persistido no evento.

**Why:** O banco nao armazena explicitamente quais tokens ficaram sem match na sync original. O titulo salvo e a fonte de verdade suficiente para recalcular o conjunto reconhecivel sem nova chamada ao Google.

**Expected mutation rule:**
- se o conjunto reconhecivel atual for estritamente maior que o conjunto persistido, adicionar apenas os novos servicos;
- se nao houver novos servicos, nao persistir mudanca;
- se o evento era nao identificado e agora ganhou servicos, ele passa a identificado normalmente.

**Alternatives considered:**
- Buscar apenas por substring do novo servico no titulo via query SQL: util como futura otimizacao, mas mais fragil e dependente de heuristica textual no banco.
- Reprocessar apenas `identified=false`: insuficiente para o problema central.

### 4. Preservar snapshots existentes e recalcular apenas o total agregado do evento quando houver enriquecimento
**Decision:** Ao adicionar um novo servico faltante:
- manter os `CalendarEventServiceLink` ja existentes intactos;
- criar links apenas para os novos servicos reconhecidos;
- atualizar `serviceValueSnapshot` do evento para refletir a soma do conjunto completo apos o enriquecimento;
- manter `serviceDescriptionSnapshot` legado se o evento ja possuia um servico principal valido; se o evento estava sem associacao anterior, inicializar ambos normalmente.

**Why:** Os links individuais carregam o snapshot historico relevante para detalhamento e fluxo de caixa por servico. O total agregado do evento precisa refletir a nova composicao, mas isso nao exige sobrescrever o snapshot ja persistido dos servicos antigos.

**Alternatives considered:**
- Recriar todos os links e snapshots com valores atuais: rejeitada por violar historico.
- Nao atualizar `serviceValueSnapshot` do evento: rejeitada porque deixaria relatorios agregados inconsistentes com os links enriquecidos.

### 5. Disparar enriquecimento apenas quando o catalogo pode criar novos matches
**Decision:** O `ServiceCatalogService` deve disparar o enriquecimento assíncrono em:
- criacao de servico;
- atualizacao de descricao/normalizedDescription.

Mudanca apenas de valor nao deve iniciar varredura de enriquecimento.

**Why:** Uma alteracao de valor sozinha nao transforma tokens antes desconhecidos em conhecidos. Disparar scan completo nesses casos acrescenta custo sem abrir novas associacoes.

**Alternatives considered:**
- Manter disparo em toda atualizacao, inclusive valor: simples, mas desperdiça processamento e amplia risco operacional.

### 6. Manter serializacao por usuario e estabilidade em sync posterior
**Decision:** O enriquecimento deve continuar sob o mesmo lock por usuario ja usado pelo sync e pelo reprocessamento atual, e o sync posterior deve tratar o conjunto enriquecido como estado canonico sem tentar duplicar ou remover associacoes equivalentes.

**Why:** O bug relatado nao pode ser trocado por regressao de concorrencia nem por instabilidade no ciclo seguinte de sync.

**Alternatives considered:**
- Tornar enriquecimento sincrono no request de cadastro: reduz concorrencia, mas piora latencia do CRUD.

## Risks / Trade-offs

- [Varredura de eventos do usuario pode ficar cara em bases grandes] -> Mitigacao: manter o fluxo assíncrono, trabalhar sob lock por usuario e considerar filtro incremental por periodo ou heuristica textual se o volume real exigir.
- [Misturar logica de replace e enrich no mesmo metodo pode gerar regressao] -> Mitigacao: separar explicitamente as duas semanticas em caminhos distintos, com testes cobrindo preservacao de snapshot.
- [Atualizar total agregado do evento sem sobrescrever snapshots individuais exige cuidado] -> Mitigacao: centralizar a regra em metodo de dominio dedicado e validar com testes de relatorio e sync posterior.
- [Descricao alterada de servico pode gerar novo match em eventos antigos enquanto o snapshot legado do primeiro servico permanece antigo] -> Mitigacao: tratar isso como comportamento intencional de preservacao historica e documentar nos testes.

## Migration Plan

1. Implementar o novo fluxo assíncrono de enriquecimento de eventos sincronizados no backend.
2. Ajustar o gatilho do `ServiceCatalogService` para chamar o enriquecimento apenas em criacao e mudanca de descricao.
3. Adicionar testes unitarios e de servico cobrindo evento parcialmente identificado, evento nao identificado e sync posterior estavel.
4. Executar a suite focada de calendar sync, reprocessamento e relatorios financeiros.
5. Publicar sem migracao de dados obrigatoria, pois o enriquecimento sera aplicado gradualmente a partir de novos cadastros/atualizacoes de servico ou quando o fluxo for acionado manualmente em testes.

**Rollback strategy:**
- Reverter o deploy caso o enriquecimento gere regressao.
- Manter os eventos ja enriquecidos; adicionar associacoes faltantes e corretivo, e nao exige limpeza de dados se a serializacao canonica estiver preservada.

## Open Questions

- Queremos expor no futuro um endpoint/manual action dedicado para reenriquecer o historico inteiro sem depender de novo cadastro de servico?
- Vale limitar inicialmente o enriquecimento a uma janela temporal configuravel se surgirem usuarios com historico muito volumoso?
