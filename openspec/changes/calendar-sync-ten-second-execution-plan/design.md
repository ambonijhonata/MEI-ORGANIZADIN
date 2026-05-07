## Context

O `POST /api/calendar/sync` continua com latência acima do esperado em conta de alto volume (~14.482 eventos), com histórico recente de ~20-23s em execução completa. O cenário-alvo desta change considera ambiente restrito (`512MB RAM`, `0.1 CPU`), no qual picos de memória, GC e custo de ORM em massa tornam-se gargalos relevantes.

Já existem otimizações aplicadas no código (batch, cache local de normalização, no-op detection e logs por etapa), porém ainda faltam validação final de benchmark no ambiente-alvo e um plano de execução objetivo para fechar a meta de `<=10s` sem regressão funcional.

## Goals / Non-Goals

**Goals:**
- Definir plano de execução faseado, com critérios objetivos de aceite por etapa.
- Reduzir custo total de sync em cenário de alto volume preservando contrato HTTP e semântica de negócio.
- Minimizar uso transitório de memória e trabalho redundante de processamento/persistência.
- Formalizar rollout com rollback rápido por configuração.

**Non-Goals:**
- Alterar payload/HTTP dos endpoints de calendário.
- Substituir protocolo de sincronização do Google por outro mecanismo fora do `syncToken` atual.
- Introduzir processamento distribuído ou infraestrutura adicional nesta change.

## Decisions

### 1) Execução em pipeline por página para reduzir memória de pico
- Decisão: processar e persistir por página/lote de eventos do Google, evitando acumular toda a massa em memória antes da escrita.
- Racional: em `512MB`, o acúmulo integral de eventos e entidades aumenta pressão de heap/GC.
- Alternativas consideradas:
- manter modelo atual de lista única em memória (simples, porém mais custoso em memória);
- pipeline com fila externa (ganho potencial maior, complexidade e escopo elevados).

### 2) Separar janelas transacionais longas
- Decisão: reduzir duração de transações abrangentes, isolando fetch remoto de janelas de escrita/estado sempre que possível.
- Racional: transações longas ampliam lock time, retenção de contexto JPA e custo de flush.
- Alternativas consideradas:
- transação única end-to-end (mais simples, maior risco de contenção);
- múltiplas transações por chunk (maior controle, mais cuidado com consistência).

### 3) Lookup local com menor carga de associação
- Decisão: privilegiar lookup leve para decisão de no-op e só carregar associações completas quando necessário.
- Racional: `JOIN FETCH` amplo para grandes conjuntos pode inflar tempo de consulta e memória.
- Alternativas consideradas:
- manter preload completo sempre (robusto, porém caro);
- projeções/queries segmentadas (melhor custo, mais implementação).

### 4) Persistência orientada a delta efetivo
- Decisão: manter e reforçar estratégia de pular escrita quando não houver mudança semântica, com foco em reduzir `db_write_ms`.
- Racional: em incremental, parte relevante dos itens pode ser no-op.
- Alternativas consideradas:
- persistir sempre (menos lógica, mais I/O);
- caminho híbrido ORM + SQL em massa para hot path (mais desempenho, maior complexidade).

### 5) Gate operacional de performance para concluir a change
- Decisão: exigir benchmark formal no ambiente-alvo com dataset de referência antes de concluir.
- Racional: sem medição reproduzível, a meta de `<=10s` não é verificável.
- Alternativas consideradas:
- validar apenas local de dev padrão (rápido, baixa confiabilidade para produção alvo).

## Risks / Trade-offs

- [Mudança em pipeline por página pode afetar contadores/fallback] -> Mitigação: regressão contratual obrigatória (`200`, `403`, fallback `410`) e comparação de contagens.
- [Batch agressivo em ambiente fraco gerar picos de memória] -> Mitigação: parâmetros configuráveis por ambiente e testes de sensibilidade de batch.
- [Otimização de lookup reduzir cobertura de associação] -> Mitigação: testes específicos de associação de serviços/clientes e no-op equivalence.
- [Objetivo de 10s não ser atingível com 0.1 CPU em full sync] -> Mitigação: documentar limites, manter UX não bloqueante e registrar p50/p95/p99 para decisão de produto.

## Migration Plan

1. Implementar fase 1: pipeline/lookup leve + ajuste transacional mínimo viável.
2. Executar benchmark parcial e comparar `sync_total_ms` + breakdown por etapa.
3. Implementar fase 2: reforço de persistência delta/híbrida conforme gargalo observado.
4. Reexecutar benchmark completo no dataset de 14.482 eventos no ambiente-alvo.
5. Validar regressão funcional e de semântica (`syncToken`, fallback, contadores).
6. Rollout gradual com flags/configuração de tuning por ambiente.

Rollback:
- Reverter tuning para valores conservadores (`GOOGLE_CALENDAR_SYNC_MAX_RESULTS`, `CALENDAR_SYNC_BATCH_SIZE`, `CALENDAR_SYNC_BATCH_CLEAR_ENABLED`, `POSTGRES_REWRITE_BATCHED_INSERTS`).
- Desativar caminhos novos por feature flag/config e retornar ao fluxo anterior sem alterar contrato.

## Open Questions

- O critério de aceite final será `p95 <= 10s` ou pior caso único `<= 10s`?
- Qual janela de dados (histórico total vs recorte temporal) representa o ambiente real esperado no primeiro sync?
- Em caso de limite físico do ambiente (`0.1 CPU`), qual limite de tempo aceitável para full sync inicial será aprovado pelo produto?
