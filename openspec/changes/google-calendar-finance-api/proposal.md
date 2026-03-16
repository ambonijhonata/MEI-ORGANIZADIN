## Why

Profissionais autônomos e pequenos negócios precisam acompanhar faturamento e fluxo de caixa a partir dos agendamentos já cadastrados no Google Agenda, sem entrada manual de dados repetitivos. Atualmente não existe uma API backend que integre autenticação Google, sincronização de eventos do calendário, identificação automática de serviços e geração de relatórios financeiros em uma única solução multiusuário.

## What Changes

- Criação de uma API backend completa em Java 21 + Spring Boot
- Autenticação unificada via Google ID Token (sem token próprio da aplicação)
- Bootstrap de integração Google Calendar via authorization code → OAuth tokens
- CRUD de catálogo de serviços por usuário (descrição + valor), com bloqueio de exclusão quando houver vínculos
- Sincronização de eventos do calendário principal do Google (full sync, incremental sync, resync)
- Identificação automática de serviço por comparação normalizada do título do evento com descrição do serviço
- Persistência do valor histórico (snapshot) no evento sincronizado no momento da associação
- Reprocessamento assíncrono de eventos não identificados após cadastro/alteração de serviço
- Remoção local de eventos excluídos no Google
- Tratamento de revogação de OAuth com marcação de integração inválida
- Relatório de faturamento (total consolidado, máximo 12 meses)
- Relatório de fluxo de caixa (série temporal, máximo 7 dias corridos)
- Metadados de atualidade dos dados em todos os relatórios
- Isolamento completo de dados por usuário autenticado
- TDD obrigatório com JUnit 5 e Rest Assured

## Capabilities

### New Capabilities
- `google-auth`: Autenticação via Google ID Token, resolução de usuário interno por google_sub, troca de authorization code por OAuth tokens
- `service-catalog`: CRUD de serviços do usuário (descrição, valor), normalização de descrição, bloqueio de exclusão com vínculos, unicidade lógica
- `calendar-sync`: Sincronização full/incremental/resync com Google Calendar, identificação de serviço por título, snapshot de valor histórico, remoção de eventos excluídos, reprocessamento de não identificados
- `financial-reports`: Relatório de faturamento (consolidado, max 12 meses) e fluxo de caixa (série temporal, max 7 dias), verificação de atualidade dos dados, metadados de sincronização
- `oauth-lifecycle`: Tratamento de revogação/invalidação de refresh_token, marcação de integração como inválida, interrupção de sync automático, exigência de reautenticação

### Modified Capabilities

## Impact

- **Código**: Projeto Spring Boot novo com camadas Controller, Service, Domain e Infrastructure
- **APIs**: Endpoints para autenticação, serviços, eventos, sincronização, relatórios e status de integração
- **Dependências**: Spring Boot, Spring Security, Spring Data JPA, Bean Validation, Google OAuth/Calendar API client, JUnit 5, Rest Assured, Flyway/Liquibase, HikariCP
- **Sistemas**: Base de dados relacional própria, integração com Google OAuth 2.0 e Google Calendar API
- **Performance**: Metas de p50=80ms, p95=300ms, p99=500ms, 1000 RPS, 200 usuários concorrentes, erro <0.1%
