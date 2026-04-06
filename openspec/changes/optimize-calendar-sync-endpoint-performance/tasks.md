## 1. Baseline e guardrails de compatibilidade

- [x] 1.1 Mapear e documentar comportamento atual de `POST /api/calendar/sync` (status HTTP, payload, contadores e fallback de token expirado) como referencia de regressao.
- [x] 1.2 Adicionar/ajustar testes de regressao funcional para garantir preservacao de contrato (`200`, `403 INTEGRATION_REVOKED`, `403 GOOGLE_API_FORBIDDEN`, fallback full resync).
- [x] 1.3 Instrumentar metricas/logs baseline de duracao por etapa (`google_fetch`, processamento, persistencia, total) no fluxo atual.

## 2. Otimizacao de lookup em lote

- [x] 2.1 Implementar pre-carga de eventos locais por `googleEventId` para o usuario da execucao de sync.
- [x] 2.2 Implementar cache por execucao para clientes por `normalizedName`, incluindo estrategia segura de create-if-missing sem alterar regra de negocio.
- [x] 2.3 Implementar cache por execucao para servicos por `normalizedDescription`, preservando matching atual.
- [x] 2.4 Remover caminhos de consulta repetitiva por evento (N+1) no processamento de sync mantendo mesma semantica funcional.

## 3. Otimizacao de persistencia e fetch Google

- [x] 3.1 Introduzir persistencia em lote para criacao/atualizacao de eventos com flush/clear por chunk configuravel.
- [x] 3.2 Garantir consistencia de associacoes (cliente/servico/snapshots) no modelo em lote, incluindo casos de atualizacao e limpeza de vinculos.
- [x] 3.3 Otimizar requisicoes para Google Calendar com `maxResults` e `fields` apropriados sem alterar semantica de paginacao e `nextSyncToken`.
- [x] 3.4 Validar que fallback por token expirado continua executando full resync com mesmo comportamento de contagem e persistencia.

## 4. Controle de concorrencia por usuario

- [x] 4.1 Implementar coordenacao de sync concorrente por `userId` para evitar processamento pesado duplicado simultaneo.
- [x] 4.2 Garantir que syncs de usuarios diferentes continuem executando em paralelo sem serializacao global.
- [x] 4.3 Cobrir cenarios concorrentes com testes para validar ausencia de regressao funcional e de duplicidade de processamento.

## 5. Validacao de desempenho e rollout seguro

- [x] 5.1 Criar teste/roteiro de carga de alto volume para sincronizacao (massa proxima ao caso real de 14k+ eventos).
- [ ] 5.2 Comparar baseline vs otimizado com metricas de `sync_total_ms` e tempos por etapa, registrando ganho e limites.
- [x] 5.3 Externalizar parametros de tuning (batch size, thresholds/flags de otimizacao) em configuracao para ajuste por ambiente.
- [x] 5.4 Definir plano de rollback operacional (desativar estrategias de batch/lock por configuracao) sem alterar contrato do endpoint.
