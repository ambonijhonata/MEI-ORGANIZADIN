## 1. Dependência e Configuração

- [x] 1.1 Adicionar dependência `springdoc-openapi-starter-webmvc-ui` ao pom.xml
- [x] 1.2 Criar classe OpenApiConfig com @Bean OpenAPI (título, versão, descrição, esquema Bearer)
- [x] 1.3 Atualizar SecurityConfig para permitir acesso público a `/swagger-ui/**`, `/v3/api-docs/**`

## 2. Anotações nos Controllers

- [x] 2.1 Anotar AuthController com @Tag e @Operation nos endpoints (login)
- [x] 2.2 Anotar ServiceCatalogController com @Tag e @Operation nos endpoints (CRUD serviços)
- [x] 2.3 Anotar CalendarController com @Tag e @Operation nos endpoints (sync, events, status)
- [x] 2.4 Anotar ReportController com @Tag e @Operation nos endpoints (revenue, cashflow)

## 3. Verificação

- [x] 3.1 Verificar que a aplicação compila e o Swagger UI carrega em /swagger-ui.html
