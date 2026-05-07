## Context

A sincronizacao de calendario passou a falhar em producao com `LazyInitializationException` no hot path de comparacao de associacoes de servico, apos ajustes recentes de performance. O erro ocorre no fluxo de `POST /api/calendar/sync`, interrompe o processamento, deixa estado `SYNCING` persistido e aparece para o cliente como `401 UNAUTHORIZED`, embora o token esteja valido.

Essa combinacao impacta confiabilidade, observabilidade e experiencia do usuario. A correção deve eliminar o acesso lazy invalido sem desfazer ganhos de desempenho recentes nem alterar contrato HTTP de sucesso do endpoint.

## Goals / Non-Goals

**Goals:**
- Remover causa raiz de `LazyInitializationException` no sync.
- Garantir classificacao correta de falhas internas de sync (nao mascarar como erro de autenticacao).
- Garantir transicao consistente de estado (`SYNCING` -> `SYNCED`/`FAILED`/`REAUTH_REQUIRED`).
- Preservar comportamento funcional e nao regredir meta de desempenho operacional.

**Non-Goals:**
- Reescrever arquitetura de autenticacao/sessao.
- Alterar payload de sucesso do endpoint de sync.
- Introduzir novo mecanismo de sincronizacao diferente de `syncToken`.

## Decisions

### 1) Tornar comparacao de associacao lazy-safe
- Decisao: no hot path de `hasServiceAssociationChanges`, comparar por snapshots/identidades pre-carregadas e IDs, sem acessar campos lazy de proxies (`service.getNormalizedDescription()` fora de sessao).
- Rationale: elimina `LazyInitializationException` sem precisar reintroduzir preloads pesados com `JOIN FETCH` global.
- Alternativas:
- Reativar lookup com eager load completo (mais seguro para lazy, pior para latencia/memoria);
- Abrir sessao longa/global (risco de custo e acoplamento transacional).

### 2) Corrigir integridade de erro entre aplicacao e seguranca
- Decisao: tratar `RuntimeException` no fluxo de sync para persistir `FAILED` com categoria adequada e resposta coerente da camada de aplicacao.
- Rationale: evita estado preso em `SYNCING` e reduz ambiguidade operacional.
- Alternativas:
- Tratar somente excecoes checked (insuficiente para erros reais do hot path).

### 3) Evitar mascaramento de falha interna como 401
- Decisao: ajustar fluxo de seguranca/dispatch de erro para que requests autenticados com falha interna nao retornem `UNAUTHORIZED` generico.
- Rationale: melhora diagnostico do app e evita refresh loops desnecessarios.
- Alternativas:
- Manter comportamento atual e tratar apenas no cliente (oculta causa raiz e aumenta retry desnecessario).

### 4) Preservar nao-regressao de performance
- Decisao: manter benchmark por etapas e validar que correcoes de confiabilidade nao pioram significativamente `sync_total_ms` e breakdown.
- Rationale: correcoes de robustez nao podem comprometer objetivo operacional.

## Risks / Trade-offs

- [Correção lazy-safe reduzir precisão de equivalência em casos de borda] -> Mitigação: testes de regressão para associação equivalente, alteração real e cenários multi-serviço.
- [Mudança de tratamento de erro alterar expectativa do cliente] -> Mitigação: manter códigos já previstos (`INTEGRATION_REVOKED`, `GOOGLE_API_FORBIDDEN`) e padronizar falha interna com código específico documentado.
- [Rollback parcial não restaurar observabilidade] -> Mitigação: rollback por commit + validação de logs `calendar_sync_summary` e erros por categoria.

## Migration Plan

1. Implementar correção lazy-safe no `CalendarSyncService`.
2. Implementar tratamento explícito de `RuntimeException` com persistência de estado FAILED.
3. Ajustar comportamento de segurança em error-dispatch para não mascarar falha interna autenticada.
4. Executar suíte de testes de sync + segurança.
5. Executar benchmark de não-regressão no cenário de alto volume.
6. Deploy canário/gradual com monitoramento de `401`, `sync failures` e `sync_total_ms`.

Rollback:
- Reverter commit da correção caso haja regressão funcional.
- Manter parâmetros de tuning atuais e comparar métricas antes/depois para decisão de reintrodução parcial.

## Open Questions

- Qual código HTTP/código de erro deve representar falha interna de sync no contrato atual (500 vs 503) para manter UX previsível no app?
- O cliente Android deve continuar tentando refresh reativo em qualquer 401 de `/api/calendar/sync` ou diferenciar por código de erro?
