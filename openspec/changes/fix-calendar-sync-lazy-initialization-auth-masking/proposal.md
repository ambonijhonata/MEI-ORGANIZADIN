## Why

A sincronizacao de calendario em producao passou a falhar com `LazyInitializationException` no fluxo de comparacao de associacoes, mantendo o estado em `SYNCING` e retornando `401 UNAUTHORIZED` ao cliente em cenarios que nao sao erro de autenticacao. Precisamos corrigir a causa raiz no backend para restaurar confiabilidade sem perder a meta de desempenho de ate 10 segundos.

## What Changes

- Corrigir o fluxo de sync para evitar acesso a proxies lazy fora de sessao no hot path de associacao de servicos/clientes.
- Garantir que falhas internas de processamento do sync sejam classificadas e persistidas como erro de sync (nao como erro de autenticacao).
- Ajustar comportamento de seguranca/erro para evitar mascaramento de falha de aplicacao em `401` quando a autenticacao esta valida.
- Preservar contratos funcionais atuais de `POST /api/calendar/sync` (payload de sucesso, fallback de token, codigos de erro ja definidos) e meta operacional de performance.

## Capabilities

### New Capabilities
- `calendar-sync-lazy-safe-execution`: Define requisitos para execucao de sync sem acesso lazy invalido e com degradacao controlada de erro interno.
- `sync-auth-error-surface-integrity`: Define requisitos para nao mascarar falhas internas como `UNAUTHORIZED` quando o token e valido.
- `calendar-sync-performance-non-regression`: Define requisitos para preservar validacao de desempenho apos correcoes de confiabilidade.

### Modified Capabilities
- Nenhuma.

## Impact

- Backend API:
- `src/main/java/com/api/calendar/CalendarSyncService.java`
- `src/main/java/com/api/calendar/CalendarEventRepository.java`
- `src/main/java/com/api/auth/GoogleIdTokenAuthenticationFilter.java`
- `src/main/java/com/api/auth/SecurityConfig.java`
- `src/main/java/com/api/common/GlobalExceptionHandler.java`
- OpenSpec:
- `openspec/changes/fix-calendar-sync-lazy-initialization-auth-masking/*`
- Novas specs de confiabilidade e nao-regressao de desempenho
