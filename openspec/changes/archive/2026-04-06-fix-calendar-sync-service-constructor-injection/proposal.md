## Why

A API deixou de inicializar por ambiguidade de construtores no `CalendarSyncService`, resultando em erro de criação de bean (`No default constructor found`). A correção precisa restaurar a inicialização do contexto Spring sem alterar contratos ou comportamento funcional dos endpoints.

## What Changes

- Corrigir a estratégia de injeção de dependências do `CalendarSyncService` para remover ambiguidade de construção de bean no Spring.
- Definir um padrão estável para construtor de produção (injeção Spring) e para criação em testes, evitando regressões de inicialização.
- Garantir, por testes, que o comportamento de `POST /api/calendar/sync` permanece inalterado em sucesso, erros e fallback.
- Manter configuração de tuning existente como comportamento compatível (sem alterar semântica da API).

## Capabilities

### New Capabilities
- `calendar-sync-service-instantiation-stability`: Define requisitos para inicialização estável do bean `CalendarSyncService` sem impacto no contrato atual da API.

### Modified Capabilities
- Nenhuma.

## Impact

- Código principal:
- `src/main/java/com/api/calendar/CalendarSyncService.java`
- Testes:
- `src/test/java/com/api/calendar/CalendarSyncServiceTest.java`
- `src/test/java/com/api/calendar/CalendarSyncServiceExtendedTest.java`
- `src/test/java/com/api/calendar/CalendarSyncServiceConcurrencyTest.java`
- Sem alteração de contrato HTTP, payloads, códigos de erro ou semântica de negócios dos endpoints existentes.
