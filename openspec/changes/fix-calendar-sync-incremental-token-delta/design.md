## Context

O endpoint `POST /api/calendar/sync` deveria operar em modo incremental apos o primeiro full sync, retornando apenas delta do Google Calendar (novos/alterados/excluidos). No ambiente atual, o comportamento observado e volume recorrente alto (aprox. 14k eventos por chamada), sugerindo queda frequente para full sync. Em paralelo, o payload de resposta apresenta `updated` inflado, porque o contador atual considera eventos existentes processados, mesmo sem mutacao persistida.

Restricoes relevantes:
- Ambiente alvo com recursos limitados (0.1 CPU e 512MB RAM).
- Contrato HTTP deve permanecer estavel (`created`, `updated`, `deleted`).
- Fluxo deve continuar tratando `410 Gone` com full resync controlado.

## Goals / Non-Goals

**Goals:**
- Garantir integridade do ciclo de `syncToken` (aquisicao, persistencia, reutilizacao e renovacao).
- Evitar full sync recorrente por configuracao/parametro incompativel ou persistencia indevida de token nulo.
- Corrigir semantica de `updated` para refletir apenas mutacoes reais persistidas.
- Melhorar observabilidade para diferenciar incremental normal, fallback por expiracao e fallback por ausencia de token.

**Non-Goals:**
- Redesenhar o parser de eventos/titulos.
- Alterar contrato de autenticacao/autorizacao do endpoint.
- Introduzir nova infraestrutura externa (fila, cache distribuido, etc.).

## Decisions

1. Preservar invariantes de incremental token na borda do cliente Google Calendar.
- Decisao: revisar montagem da requisicao full sync para remover parametros que podem impedir emissao de `nextSyncToken` e manter apenas combinacoes compativeis.
- Racional: sem `nextSyncToken` valido, o ciclo incremental nao se estabelece e o sistema reprocessa volume total.
- Alternativa considerada: manter requisicao atual e apenas logar ausencia de token. Rejeitada por nao atacar causa raiz.

2. Nao sobrescrever token persistido com valor nulo.
- Decisao: ao finalizar sync com sucesso, atualizar `sync_token` apenas quando houver `nextSyncToken` valido; em ausencia, preservar token existente e registrar evento operacional.
- Racional: evita degradacao silenciosa para full sync continuo.
- Alternativa considerada: limpar token sempre que resposta nao trouxer `nextSyncToken`. Rejeitada por amplificar full sync desnecessario.

3. Contadores baseados em mutacao efetiva.
- Decisao: `updated` deve contar somente eventos existentes com `shouldPersist=true` (ou equivalencia funcional apos mutacao), nao eventos apenas avaliados.
- Racional: melhora confiabilidade do payload e reduz falso diagnostico de escrita massiva.
- Alternativa considerada: manter contador atual e criar campo novo `processed`. Rejeitada nesta change por risco de interpretacao ambigua sem corrigir metrica principal.

4. Observabilidade explicita de modo de execucao.
- Decisao: registrar nos logs o modo (`incremental`, `full_resync_410`, `full_no_token`) e estado do token antes/depois da sync.
- Racional: acelera troubleshooting sem alterar contrato API.

## Risks / Trade-offs

- [Risco] Mudanca na requisicao Google alterar ordenacao de eventos no full sync. -> Mitigacao: ordenar localmente apenas quando necessario para processamento deterministico.
- [Risco] Preservar token antigo em resposta sem `nextSyncToken` esconder problema upstream. -> Mitigacao: emitir log estruturado de anomalia e alerta operacional.
- [Trade-off] `updated` menor apos correcao pode parecer regressao para quem monitorava numero antigo. -> Mitigacao: comunicar mudanca semantica no runbook e notas de deploy.

## Migration Plan

1. Implementar ajustes de token integrity e contadores com testes unitarios/integracao.
2. Deploy em staging com conta de alto volume.
3. Validar:
- duas syncs consecutivas com segunda chamada em volume delta reduzido;
- ausencia de fallback indevido para full sync;
- consistencia de `updated` com mutacoes reais.
4. Deploy em producao monitorando `events_received`, `updated`, `fallback_from_expired_token` e logs de token.

Rollback:
- Reverter commit da change e redeploy.
- Validar smoke de `POST /api/calendar/sync` + `GET /api/calendar/status`.

## Open Questions

- Devemos expor metrica adicional de `processed` apenas em log interno para analise de capacidade?
- Qual threshold de alerta para detectar regressao de sync incremental (ex.: `events_received` muito acima da mediana historica)?
