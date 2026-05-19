## 1. Token Integrity No Google Sync

- [x] 1.1 Ajustar requisicao de full sync no `GoogleCalendarClient` para evitar parametros incompativeis com emissao de `nextSyncToken`.
- [x] 1.2 Garantir persistencia de `syncToken` apenas quando `nextSyncToken` vier valido (nao nulo/nao vazio).
- [x] 1.3 Preservar token previamente persistido quando sync concluir sem token novo e registrar aviso operacional estruturado.

## 2. Fluxo De Fallback E Observabilidade

- [x] 2.1 Tornar explicito em log o modo de execucao (`incremental`, `full_no_token`, `full_resync_410`).
- [x] 2.2 Registrar no resumo de sync o estado de token antes/depois para facilitar diagnostico de regressao incremental.
- [x] 2.3 Validar que fallback por `410` continua limpando token e executando full resync controlado.

## 3. Correcao Dos Contadores De Resultado

- [x] 3.1 Ajustar o calculo de `updated` no `CalendarSyncService` para contar apenas eventos existentes com mutacao efetiva persistida.
- [x] 3.2 Garantir que `created` e `deleted` preservem semantica atual sem breaking change de payload.
- [x] 3.3 Revisar pontos de reconciliacao para evitar dupla contagem em cenarios de delecao incremental/full.

## 4. Testes E Validacao

- [x] 4.1 Adicionar/ajustar testes unitarios para fluxo de token: persistencia, preservacao quando ausente e rotacao quando presente.
- [x] 4.2 Adicionar/ajustar testes para confirmar que `updated` nao cresce quando `shouldPersist=false`.
- [x] 4.3 Executar suite alvo (`CalendarSyncService*`, cliente Google e testes de contrato do endpoint) e registrar evidencias.
- [ ] 4.4 Validar em staging com conta de alto volume que a segunda sync retorna delta reduzido e nao reprocessa volume total.
