## Context

O problema observado em producao nao e a duplicacao de `calendar_events`, e sim a duplicacao de linhas em `calendar_event_services` para o mesmo agendamento. Isso faz o relatorio de faturamento permanecer correto, porque ele soma `calendar_events.service_value_snapshot`, enquanto o fluxo de caixa fica inflado porque reconstrui o total a partir de `event.getServiceLinks()`.

O cenario real mais provavel e:
- o usuario faz login com agenda ja povoada;
- ocorre sync antes de existir catalogo de servicos suficiente para identificar todos os eventos;
- ao cadastrar servicos, o backend dispara `reprocessUnidentifiedEvents(userId)` de forma assincrona;
- um novo sync e disparado depois, enquanto o reprocessamento ainda pode estar gravando associacoes;
- o sistema persiste links repetidos porque o sync usa lock por usuario, mas o reprocessamento nao compartilha a mesma exclusao mutua, e o banco nao possui restricao de unicidade em `calendar_event_services`.

## Goals / Non-Goals

**Goals:**
- Garantir que cada evento mantenha apenas um conjunto canonico de links de servico.
- Eliminar a possibilidade de duplicidade causada por sync e reprocessamento concorrentes.
- Tornar a substituicao de links explicita e segura para eventos ja persistidos.
- Corrigir os dados ja corrompidos em producao antes de ativar a protecao estrutural.
- Adicionar cobertura de regressao para o fluxo real de primeiro acesso com agenda previa.

**Non-Goals:**
- Nao alterar a semantica funcional de reconhecimento de titulos ou calculo de faturamento.
- Nao redesenhar o onboarding Android neste change.
- Nao reprocessar historico para mudar snapshots corretos; o foco e remover duplicidade e preservar um conjunto canonico.

## Decisions

### 1. Centralizar a exclusao mutua por usuario para toda mutacao de associacao
**Decision:** Extrair a logica de lock por usuario para um componente compartilhado e usa-lo tanto em `CalendarSyncService` quanto em `CalendarEventReprocessor`.

**Why:** Hoje o sync e serializado por usuario, mas o reprocessamento disparado apos cadastro de servico roda com `@Async` fora desse mesmo lock. Isso deixa a janela exata em que duas rotinas escrevem links para os mesmos eventos.

**Alternatives considered:**
- Tornar o reprocessamento sincrono no request de cadastro: reduziria concorrencia, mas piora latencia e UX do cadastro.
- Usar lock distribuito/advisory lock no banco: mais robusto entre instancias, mas mais custoso para o escopo atual de uma unica aplicacao Spring.
- Manter apenas o lock do sync: insuficiente, porque a outra origem de escrita continuaria livre.

### 2. Substituir links de servico por estrategia explicita de replace
**Decision:** Para eventos ja persistidos, remover explicitamente todos os links antigos do evento no repositorio antes de inserir o novo conjunto canonico, em vez de depender apenas de `serviceLinks.clear()` com `orphanRemoval`.

**Why:** A implementacao atual depende do ciclo de vida JPA da colecao para detectar remocoes e inserir novos filhos. Em fluxos de `saveAll` em lote e entidades carregadas em execucoes distintas, isso e mais fragil do que uma estrategia explicita de replace.

**Alternatives considered:**
- Manter apenas `clear()+add()` na entidade: simples, mas foi insuficiente na pratica.
- Atualizar linha a linha com diff detalhado: funciona, mas agrega complexidade sem ganho relevante para o volume atual.

### 2.1 Separar a fase de leitura da fase de escrita no sync
**Decision:** O fluxo de sync deve resolver cliente, servicos casados, contagem de links persistidos e demais consultas JPA antes de aplicar qualquer mutacao em `CalendarEvent` que monte novos `serviceLinks`.

**Why:** O erro observado nao depende apenas de concorrencia. No fluxo atual, um evento existente podia ter os novos `serviceLinks` montados em memoria e, antes do replace explicito no banco, uma query posterior como `findOrCreateByName(...)` disparava `auto-flush` do Hibernate. Isso fazia a insercao dos novos links colidir com os antigos ainda persistidos.

**Required flow shape:**
- fase 1: carregar eventos existentes, resolver cliente, resolver servicos reconhecidos e calcular se houve mudanca, sem mutar `serviceLinks`;
- fase 2: registrar um plano de persistencia por evento com os campos e links canonicos desejados;
- fase 3: durante `persistMutations`, remover links antigos dos eventos persistidos afetados, fazer `flush`, aplicar o conjunto canonico novo e so entao salvar os eventos.

**Guardrail:** Nenhuma query JPA deve acontecer entre o momento em que um evento existente ganha novos `serviceLinks` em memoria e o momento em que os links antigos correspondentes sao removidos e sincronizados com `flush`.

**Alternatives considered:**
- Confiar apenas na restricao unica do banco: evita corrupcao silenciosa, mas continua transformando o fluxo normal em excecao de integridade.
- Trocar `FlushMode` ou desabilitar `auto-flush` localmente: mascara o sintoma, mas nao resolve a mistura perigosa entre leitura e escrita na mesma unidade de trabalho.

### 3. Proteger o banco com unicidade por chave semantica de link
**Decision:** Adicionar protecao de unicidade em `calendar_event_services` para impedir dois links semanticamente equivalentes no mesmo evento.

**Why:** Mesmo com a correcao de concorrencia, o banco precisa agir como ultima barreira contra regressao.

**Recommended shape:**
- indice unico parcial para linhas com `service_id IS NOT NULL`, cobrindo `calendar_event_id, service_id`;
- indice unico parcial para linhas com `service_id IS NULL`, cobrindo `calendar_event_id, service_description_snapshot, service_value_snapshot`.

**Why this shape:** `service_id` pode ficar nulo depois de exclusao do servico por `ON DELETE SET NULL`, entao uma regra unica so com `service_id` nao cobre todo o historico. A combinacao parcial preserva integridade tanto para links vivos quanto para snapshots historicos sem FK ativa.

**Alternatives considered:**
- um unico indice com `coalesce(service_id, -1)`: simples, mas mistura regra de FK ativa com sentinela artificial.
- unicidade completa em `calendar_event_id, service_id, service_description_snapshot, service_value_snapshot`: protege, mas fica mais permissiva do que o desejado para o caso com `service_id` conhecido.

### 4. Remediar os dados de forma transacional antes de ativar a restricao
**Decision:** Executar o saneamento SQL na propria migracao Flyway antes de criar os indices unicos, preservando o script manual apenas como fallback operacional.

**Why:** O deploy falhou quando a restricao tentou subir sobre dados ainda duplicados. Embutir a limpeza na migracao elimina a dependencia de um passo manual previo e garante que a restricao seja criada sobre um estado canonico no mesmo rollout.

**Migration SQL for remediation:**

```sql
WITH duplicate_service_rows AS (
    SELECT id
    FROM (
        SELECT
            id,
            ROW_NUMBER() OVER (
                PARTITION BY calendar_event_id, service_id
                ORDER BY id
            ) AS rn
        FROM calendar_event_services
        WHERE service_id IS NOT NULL
    ) ranked
    WHERE rn > 1
),
duplicate_snapshot_rows AS (
    SELECT id
    FROM (
        SELECT
            id,
            ROW_NUMBER() OVER (
                PARTITION BY calendar_event_id, service_description_snapshot, service_value_snapshot
                ORDER BY id
            ) AS rn
        FROM calendar_event_services
        WHERE service_id IS NULL
    ) ranked
    WHERE rn > 1
),
rows_to_delete AS (
    SELECT id FROM duplicate_service_rows
    UNION
    SELECT id FROM duplicate_snapshot_rows
)
DELETE FROM calendar_event_services
WHERE id IN (SELECT id FROM rows_to_delete);
```

**Operational fallback script:** manter um script separado para diagnostico e limpeza controlada quando quisermos medir impacto antes do deploy ou atuar manualmente em um ambiente especifico.

**Verification query after cleanup:**

```sql
SELECT
    calendar_event_id,
    service_id,
    service_description_snapshot,
    service_value_snapshot,
    COUNT(*) AS occurrences
FROM calendar_event_services
GROUP BY
    calendar_event_id,
    service_id,
    service_description_snapshot,
    service_value_snapshot
HAVING COUNT(*) > 1;
```

### 5. Cobrir o fluxo real com testes de regressao
**Decision:** Adicionar testes para o encadeamento completo: sync sem servicos, cadastro de servicos, reprocessamento assincrono, sync posterior e conferencia do fluxo de caixa.

**Why:** O defeito apareceu na interseccao de modulos, nao em um metodo isolado.

## Risks / Trade-offs

- [Lock por usuario reduzir paralelismo] -> Mitigacao: o lock sera restrito apenas a fluxos que mutam associacao de servicos; leitura de relatorios e listagem seguem independentes.
- [Delete-and-reinsert pode alterar ids dos links restantes] -> Mitigacao: os ids de `calendar_event_services` nao sao parte de contrato externo; preservar o snapshot semantico e mais importante que preservar ids tecnicos.
- [Criacao de indice unico falhar por dados residuais] -> Mitigacao: a migracao limpa duplicidades no mesmo criterio dos indices antes de cria-los; manter query de verificacao e script manual como fallback.
- [Reprocessamento assincrono ainda competir entre multiplas instancias da aplicacao] -> Mitigacao: no escopo atual, usar lock compartilhado na JVM e unicidade no banco; se houver horizontal scaling depois, evoluir para lock distribuido/advisory lock.

## Migration Plan

1. Rodar a query de diagnostico em producao para medir grupos duplicados e registrar baseline.
2. Publicar migracao Flyway com o saneamento transacional e os indices unicos parciais.
3. Validar com a query de verificacao que nao restaram grupos duplicados.
4. Publicar a mudanca de codigo que serializa sync e reprocessamento e substitui links por replace explicito.
5. Executar testes de regressao e smoke test manual no fluxo: usuario sem servicos, sync, cadastro, reprocessamento, relatorio de fluxo de caixa.
6. Monitorar logs e consultar amostras do banco nas primeiras execucoes para confirmar ausencia de novas duplicidades.

**Rollback strategy:**
- Se a mudanca de codigo falhar, reverter deploy mantendo os dados ja saneados.
- Se a criacao do indice falhar, interromper a migracao, corrigir os dados restantes e reexecutar.
- Nao reintroduzir linhas duplicadas no rollback; a limpeza de dados e corretiva e deve ser preservada.

## Open Questions

- A aplicacao backend roda hoje em apenas uma instancia ou existe plano imediato de escalar horizontalmente?
- Queremos transformar o reprocessamento pos-cadastro em fila dedicada no futuro, ou o lock compartilhado em memoria atende o horizonte atual do TCC?
