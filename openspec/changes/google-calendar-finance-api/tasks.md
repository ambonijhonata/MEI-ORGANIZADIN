## 1. Fundação do Projeto

- [x] 1.1 Criar projeto Spring Boot com Java 21 (Spring Web, Spring Security, Spring Data JPA, Bean Validation)
- [x] 1.2 Configurar PostgreSQL como banco de dados e HikariCP como connection pool
- [x] 1.3 Configurar Flyway para versionamento de migrations
- [x] 1.4 Configurar estrutura de testes com JUnit 5 e Rest Assured
- [x] 1.5 Configurar Testcontainers com PostgreSQL para testes de integração
- [x] 1.6 Definir estrutura de pacotes por feature (auth, user, servicecatalog, calendar, report, google, common)
- [x] 1.7 Implementar handler global de exceções com respostas padronizadas de erro

## 2. Autenticação Google (google-auth)

- [ ] 2.1 Escrever testes unitários para validação do Google ID Token (assinatura, aud, iss, exp)
- [x] 2.2 Implementar GoogleIdTokenValidator com validação de assinatura, aud, iss, exp
- [x] 2.3 Escrever testes unitários para resolução de usuário interno a partir do google_sub
- [x] 2.4 Implementar entidade User e UserRepository
- [x] 2.5 Criar migration para tabela de usuários (id, google_sub, email, name, created_at, updated_at)
- [x] 2.6 Implementar AuthenticatedUserResolver que resolve/cria usuário a partir do sub do ID Token
- [x] 2.7 Configurar Spring Security com filtro bearer para validar Google ID Token em cada requisição
- [x] 2.8 Implementar SecurityContext com AuthenticatedUser (userId, googleSub, email, name)
- [ ] 2.9 Escrever testes unitários para troca de authorizationCode por tokens OAuth
- [x] 2.10 Implementar GoogleOAuthClient para trocar authorizationCode por access_token e refresh_token
- [x] 2.11 Criar migration para tabela de credenciais OAuth (user_id, access_token, refresh_token, expires_at)
- [x] 2.12 Implementar endpoint de autenticação inicial (recebe idToken + authorizationCode)
- [ ] 2.13 Escrever testes de integração (Rest Assured) para autenticação inicial com payload válido e inválido
- [ ] 2.14 Escrever testes de integração para rejeição de token expirado, aud inválido, iss inválido e ausência de header

## 3. Catálogo de Serviços (service-catalog)

- [x] 3.1 Escrever testes unitários para normalização de descrição (trim, colapso espaços, lowercase, remoção acentos)
- [x] 3.2 Implementar ServiceDescriptionNormalizer
- [x] 3.3 Escrever testes unitários para cadastro de serviço (validação, duplicidade, persistência)
- [x] 3.4 Implementar entidade Service e ServiceRepository
- [x] 3.5 Criar migration para tabela de serviços (id, user_id, description, normalized_description, value, created_at, updated_at)
- [x] 3.6 Implementar ServiceCatalogService com criação de serviço (valida, normaliza, persiste)
- [x] 3.7 Implementar endpoint POST de criação de serviço
- [x] 3.8 Escrever testes unitários para listagem de serviços (filtro por usuário autenticado)
- [x] 3.9 Implementar endpoint GET de listagem de serviços do usuário autenticado
- [x] 3.10 Escrever testes unitários para atualização de serviço (validação, normalização, disparo de reprocessamento)
- [x] 3.11 Implementar endpoint PUT de atualização de serviço
- [x] 3.12 Escrever testes unitários para exclusão de serviço (bloqueio quando há vínculos)
- [x] 3.13 Implementar endpoint DELETE de exclusão de serviço com verificação de vínculos
- [ ] 3.14 Escrever testes de integração (Rest Assured) para CRUD completo de serviços
- [ ] 3.15 Escrever testes de segurança para isolamento entre usuários no catálogo de serviços

## 4. Sincronização com Google Calendar (calendar-sync)

- [x] 4.1 Escrever testes unitários para lógica de decisão entre full sync e incremental sync
- [x] 4.2 Implementar entidade SyncState e SyncStateRepository (user_id, sync_token, last_sync_at, status, error_category)
- [x] 4.3 Criar migration para tabela de estado de sincronização
- [x] 4.4 Implementar entidade CalendarEvent e CalendarEventRepository
- [x] 4.5 Criar migration para tabela de eventos sincronizados (id, user_id, google_event_id, title, normalized_title, event_start, event_end, service_id, service_description_snapshot, service_value_snapshot, identified, created_at, updated_at)
- [x] 4.6 Implementar GoogleCalendarClient para buscar eventos do calendário principal (full e incremental via syncToken)
- [x] 4.7 Escrever testes unitários para matching entre título do evento e serviço cadastrado
- [x] 4.8 Implementar CalendarEventServiceMatcher (normaliza título, busca serviço por descrição normalizada do mesmo usuário)
- [x] 4.9 Escrever testes unitários para persistência do valor snapshot no evento
- [x] 4.10 Implementar CalendarSyncService com lógica de full sync (busca tudo, identifica serviços, persiste eventos e sync state)
- [x] 4.11 Implementar lógica de incremental sync (usa syncToken, aplica inserts/updates/deletes)
- [x] 4.12 Implementar fallback de full resync quando Google retorna 410 Gone
- [x] 4.13 Escrever testes unitários para remoção local de evento excluído no Google
- [x] 4.14 Implementar remoção local de eventos deletados no Google durante incremental sync
- [ ] 4.15 Escrever testes unitários para reprocessamento de eventos não identificados
- [x] 4.16 Implementar reprocessamento assíncrono (@Async) de eventos não identificados após criação/alteração de serviço
- [x] 4.17 Implementar endpoint de disparo manual de sincronização
- [x] 4.18 Implementar endpoint de leitura paginada de eventos sincronizados do usuário autenticado
- [ ] 4.19 Escrever testes de integração para sincronização (full sync, incremental sync, resync, exclusão de eventos)
- [ ] 4.20 Escrever testes de integração para leitura de eventos com isolamento por usuário e paginação

## 5. Ciclo de Vida OAuth (oauth-lifecycle)

- [x] 5.1 Escrever testes unitários para detecção de revogação de OAuth (distinguir erro transitório de permanente)
- [x] 5.2 Implementar lógica de detecção de revogação no GoogleCalendarClient e GoogleOAuthClient
- [x] 5.3 Escrever testes unitários para marcação de integração como REAUTH_REQUIRED
- [x] 5.4 Implementar atualização de SyncState com status REAUTH_REQUIRED, timestamp e reason
- [x] 5.5 Escrever testes unitários para interrupção de sync automático após revogação
- [x] 5.6 Implementar guard no CalendarSyncService que verifica status de integração antes de sincronizar
- [ ] 5.7 Escrever testes unitários para preservação de dados locais após revogação
- [x] 5.8 Implementar endpoint de consulta do status da integração Google do usuário
- [x] 5.9 Implementar re-autenticação que limpa status inválido e restaura integração
- [x] 5.10 Implementar retry com backoff exponencial para erros transitórios do Google
- [ ] 5.11 Escrever testes de integração para fluxo de revogação e re-autenticação

## 6. Relatórios Financeiros (financial-reports)

- [x] 6.1 Escrever testes unitários para validação de período do relatório de faturamento (máximo 12 meses)
- [x] 6.2 Escrever testes unitários para validação de período do relatório de fluxo de caixa (máximo 7 dias)
- [x] 6.3 Escrever testes unitários para verificação de atualidade dos dados (política de freshness)
- [x] 6.4 Escrever testes unitários para composição do relatório de faturamento (soma valores snapshot de eventos elegíveis)
- [x] 6.5 Implementar RevenueReportService (valida período, verifica freshness, seleciona eventos elegíveis, soma valores, monta metadados)
- [x] 6.6 Implementar endpoint de relatório de faturamento
- [x] 6.7 Escrever testes unitários para composição do relatório de fluxo de caixa (série temporal por dia)
- [x] 6.8 Implementar CashFlowReportService (valida período, verifica freshness, agrupa valores por dia, ordena cronologicamente, monta metadados)
- [x] 6.9 Implementar endpoint de relatório de fluxo de caixa
- [x] 6.10 Escrever testes unitários para metadados de relatório (dataUpToDate, lastSyncAt, reauthRequired)
- [ ] 6.11 Escrever testes unitários para exclusão de eventos não identificados dos relatórios
- [ ] 6.12 Escrever testes unitários para garantia de uso de valor histórico (não valor atual do catálogo)
- [ ] 6.13 Escrever testes de integração para relatório de faturamento (cálculo correto, período inválido, dados stale)
- [ ] 6.14 Escrever testes de integração para relatório de fluxo de caixa (série temporal correta, período inválido, integração revogada)

## 7. Segurança e Isolamento

- [ ] 7.1 Escrever testes de segurança: usuário A não acessa dados do usuário B (serviços, eventos, relatórios)
- [ ] 7.2 Escrever testes de segurança: token inválido, expirado, ausente
- [ ] 7.3 Escrever testes de segurança: ausência de IDOR (manipulação de IDs na URL/body)
- [ ] 7.4 Escrever testes de segurança: userId do body é ignorado, só vale o do security context
- [ ] 7.5 Verificar que tokens OAuth nunca são logados

## 8. Otimização e Performance

- [x] 8.1 Criar índices compostos nas tabelas de eventos (user_id, event_start) e serviços (user_id, normalized_description)
- [x] 8.2 Tuning de HikariCP (max pool size, connection timeout, idle timeout)
- [x] 8.3 Implementar DTOs enxutos para respostas de API (evitar campos desnecessários)
- [ ] 8.4 Configurar logs estruturados com correlation ID (sem logar tokens)
- [ ] 8.5 Configurar testes de carga com k6 (p50, p95, p99, throughput, taxa de erro)
- [ ] 8.6 Profiling e ajuste de consultas de relatórios para garantir metas de latência
