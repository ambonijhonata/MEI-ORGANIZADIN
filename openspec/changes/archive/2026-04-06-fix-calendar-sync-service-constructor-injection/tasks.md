## 1. Correcao de instanciação do bean

- [x] 1.1 Ajustar `CalendarSyncService` para ter estratégia explicita e deterministica de construcao de producao no Spring.
- [x] 1.2 Garantir que a estrategia escolhida nao exige construtor vazio e elimina a ambiguidade entre construtores.
- [x] 1.3 Revisar wiring de propriedades (`batch-size`, `batch-clear-enabled`) para manter defaults e comportamento atual.

## 2. Compatibilidade funcional e testes

- [x] 2.1 Adaptar testes unitarios impactados pela forma de construcao do `CalendarSyncService`.
- [x] 2.2 Validar que cenarios de contrato de `POST /api/calendar/sync` permanecem inalterados (sucesso, erros 403, fallback 410).
- [x] 2.3 Executar suite de testes focada em sync e registrar resultado de regressao.

## 3. Validacao operacional e seguranca de rollout

- [x] 3.1 Validar startup local da API via `run.bat` confirmando criacao do bean `CalendarSyncService`.
- [x] 3.2 Confirmar que nao houve alteracao de contrato HTTP/payload em endpoints existentes.
- [x] 3.3 Documentar criterio de aceite e rollback (reversao da alteracao de construtor) para deploy seguro.
