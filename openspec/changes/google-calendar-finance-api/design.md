## Context

Este projeto é um TCC (Trabalho de Conclusão de Curso) para construir uma API backend que permite profissionais autônomos acompanharem faturamento e fluxo de caixa a partir de agendamentos do Google Agenda. Não existe código prévio — é um projeto greenfield em Java 21 + Spring Boot.

A API será consumida por um aplicativo Android que faz login com Google e envia `idToken` + `authorizationCode` no bootstrap. Chamadas subsequentes usam o `idToken` como bearer token. O backend não emite token próprio.

Restrições: TDD obrigatório, metas de performance agressivas (p50=80ms, 1000 RPS), isolamento total de dados por usuário.

## Goals / Non-Goals

**Goals:**
- API REST multiusuário com autenticação unificada via Google
- Sincronização bidirecional (Google → base local) com full sync, incremental sync e resync
- Identificação automática de serviços por comparação normalizada de texto
- Relatórios financeiros com valor histórico preservado (snapshot)
- Transparência sobre atualidade dos dados nos relatórios
- Tratamento explícito de revogação OAuth
- Cobertura completa com TDD (JUnit 5 + Rest Assured)

**Non-Goals:**
- Interface de usuário (frontend/mobile) — responsabilidade do aplicativo
- Múltiplos calendários por usuário — apenas calendário principal
- Token próprio da API — usa exclusivamente Google ID Token
- Notificações push para o aplicativo
- Relatórios com dados em tempo real do Google (leituras sempre da base local)
- Integração com outros provedores de calendário além do Google

## Decisions

### 1. Autenticação: Google ID Token como bearer direto

**Decisão**: Usar o `idToken` do Google diretamente como bearer token em todas as requisições, sem emitir JWT/session próprios.

**Alternativas consideradas**:
- Emitir JWT próprio após validação do Google → Adicionaria complexidade de gerenciamento de sessão sem benefício claro para o escopo do TCC
- Session-based auth → Incompatível com API REST stateless

**Rationale**: Simplifica a arquitetura, elimina gerenciamento de refresh de token próprio. O `idToken` do Google já contém `sub`, `email`, `name` e é validável via chaves públicas do Google.

### 2. Persistência: PostgreSQL com Spring Data JPA

**Decisão**: Usar PostgreSQL como banco relacional com Spring Data JPA + Flyway para migrations.

**Alternativas consideradas**:
- MongoDB → Flexibilidade de schema, mas perderia integridade referencial natural para o modelo (serviço ↔ evento)
- H2 em produção → Insuficiente para metas de performance e concorrência

**Rationale**: O modelo é fortemente relacional (usuário → serviços → eventos). PostgreSQL suporta as metas de performance, oferece indexação adequada para consultas por período e agregações financeiras, e HikariCP integra nativamente com Spring Boot.

### 3. Sincronização: Google Calendar Sync API com syncToken

**Decisão**: Usar a estratégia de `syncToken` da Google Calendar API para incremental sync.

**Rationale**: O Google Calendar API retorna um `nextSyncToken` ao final de cada listagem completa. Nas chamadas seguintes, usar esse token retorna apenas eventos criados/alterados/removidos. Quando o token expira, a API retorna 410 Gone, sinalizando necessidade de full resync.

### 4. Identificação de serviço: Comparação por campo normalizado

**Decisão**: Normalizar tanto a descrição do serviço (no cadastro) quanto o título do evento (no sync) e comparar por igualdade exata do texto normalizado.

**Normalização**: trim → colapso de espaços → lowercase → remoção de acentos (Normalizer NFD + strip diacritics).

**Rationale**: Simples, determinístico e previsível. Evita falsos positivos de matching fuzzy.

### 5. Valor histórico: Snapshot no evento sincronizado

**Decisão**: Copiar `serviceId`, `serviceDescription` e `servicePrice` para o registro do evento no momento da associação.

**Rationale**: Desacopla o relatório do cadastro atual de serviços. Alterações futuras de preço não afetam eventos já associados.

### 6. Relatórios: Consulta direta na base local com metadados de sincronização

**Decisão**: Relatórios são queries agregadas sobre a tabela de eventos sincronizados, acompanhados de metadados sobre o estado de sincronização.

**Política de atualidade**: Sincronização é considerada atualizada se ocorreu nos últimos 30 minutos (configurável). Se não, o relatório é emitido com flag `dataUpToDate=false` e `lastSyncAt` explícito.

### 7. Reprocessamento: Async após cadastro/alteração de serviço

**Decisão**: Ao criar ou alterar um serviço, disparar `@Async` para reconciliar eventos não identificados do mesmo usuário.

**Rationale**: Não bloqueia a resposta do CRUD de serviços. Permite que eventos sincronizados antes do cadastro do serviço sejam retroativamente associados.

### 8. Camadas da aplicação

**Decisão**: Organização em 4 camadas — Controller, Service, Domain, Infrastructure — com pacotes por feature.

```
com.api
  ├── auth/          (controller, service, security config, callback controller)
  ├── user/          (entity, repository)
  ├── servicecatalog/ (controller, service, entity, repository, normalizer)
  ├── client/        (controller, service, entity, repository)
  ├── calendar/      (controller, service, entity, repository, sync, matcher, reprocessor)
  ├── report/        (controller, service)
  ├── google/        (OAuth client, Calendar client, retryable client)
  └── common/        (exception handling, DTOs base, validation, OpenAPI config)
```

### 9. Tratamento global de erros com mapeamento padronizado

**Decisão**: Centralizar o tratamento de exceções em um `GlobalExceptionHandler` com mapeamento explícito de cada tipo de exceção para um HTTP status e código de erro.

**Mapeamentos implementados**:
- `InvalidTokenException` → 401 UNAUTHORIZED (INVALID_TOKEN)
- `OAuthExchangeException` → 502 BAD_GATEWAY (OAUTH_EXCHANGE_FAILED)
- `ResourceNotFoundException` → 404 NOT_FOUND (NOT_FOUND)
- `BusinessException` → 422 UNPROCESSABLE_ENTITY (BUSINESS_ERROR)
- `InvalidPeriodException` → 400 BAD_REQUEST (INVALID_PERIOD)
- `IntegrationRevokedException` → 403 FORBIDDEN (INTEGRATION_REVOKED)
- `MethodArgumentNotValidException` → 400 BAD_REQUEST (VALIDATION_ERROR, com field errors)
- `ConstraintViolationException` → 400 BAD_REQUEST (VALIDATION_ERROR)

**Formato padrão**: Todas as respostas de erro incluem `status` (int), `code` (String), `message` (String), `timestamp` (Instant).

## Risks / Trade-offs

**[ID Token expiration curta (~1h)]** → O aplicativo deve renovar o ID Token antes de cada chamada. A API valida expiração e retorna 401 se expirado. Mitigação: o app Android renova silenciosamente via Google Sign-In SDK.

**[syncToken expiração]** → Google pode invalidar o syncToken a qualquer momento (retorna 410). Mitigação: fallback automático para full resync, já previsto na arquitetura.

**[Rate limits Google Calendar API]** → Muitos usuários sincronizando simultaneamente podem atingir quotas. Mitigação: rate limiting no disparo de sincronizações, retry com backoff exponencial.

**[Concorrência no reprocessamento]** → Reprocessamento async pode conflitar com sync simultâneo. Mitigação: operações de associação devem ser idempotentes — reassociar com mesmo serviço/valor não causa inconsistência.

**[Performance de relatórios com muitos eventos]** → Agregações em períodos longos podem ser lentas. Mitigação: índices compostos em (userId, eventStart), limites obrigatórios de período (7 dias / 12 meses), paginação.
