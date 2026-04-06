## 1. Tuning imediato de configuracao

- [x] 1.1 Ajustar configuracoes de sync (`GOOGLE_CALENDAR_SYNC_MAX_RESULTS=2500`, `CALENDAR_SYNC_BATCH_SIZE=1000`, `CALENDAR_SYNC_BATCH_CLEAR_ENABLED=true`).
- [x] 1.2 Habilitar `reWriteBatchedInserts=true` na configuracao JDBC do PostgreSQL.
- [x] 1.3 Validar baseline pos-tuning com logs de etapa (`google_fetch_ms`, `db_lookup_ms`, `processing_ms`, `db_write_ms`, `sync_total_ms`).

## 2. Otimizacao de lookup e CPU em full sync

- [x] 2.1 Implementar preload de eventos locais por usuario para full sync em vez de lookup com `IN` gigante.
- [x] 2.2 Implementar cache local de normalizacao (`raw -> normalized`) por execucao de sync.
- [x] 2.3 Refatorar hot path de parsing/normalizacao para reduzir uso de regex repetitivo (precompiled/manual path), preservando semantica.
- [x] 2.4 Adicionar testes de regressao para garantir cobertura de dados e equivalencia de parsing/normalizacao.

## 3. Eliminacao de trabalho desnecessario

- [x] 3.1 Implementar deteccao de no-op para pular persistencia de eventos sem mudanca efetiva.
- [x] 3.2 Evitar recomputar/reescrever associacoes de servicos quando o conjunto atual ja e equivalente.
- [x] 3.3 Cobrir com testes cenarios de "evento inalterado" e "associacao equivalente".

## 4. Validacao de meta e seguranca funcional

- [ ] 4.1 Executar benchmark comparativo baseline vs otimizado com dataset de 14.354 eventos.
- [ ] 4.2 Confirmar alcance de `sync_total_ms <= 10000` no ambiente-alvo definido.
- [x] 4.3 Validar preservacao de contrato funcional (`200` com contadores, erros `403`, fallback de token expirado).
- [x] 4.4 Documentar resultado final, limites e procedimento de rollback por configuracao.
