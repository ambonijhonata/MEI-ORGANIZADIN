## Why

A API mei-organizadin possui 10 endpoints implementados mas nenhuma documentação interativa. Swagger/OpenAPI permite que desenvolvedores (e a banca do TCC) explorem e testem os endpoints diretamente pelo navegador, além de servir como documentação viva que se mantém sincronizada com o código.

## What Changes

- Adição da dependência SpringDoc OpenAPI (springdoc-openapi-starter-webmvc-ui)
- Configuração do Swagger UI acessível em `/swagger-ui.html`
- Anotações OpenAPI nos controllers existentes (descrições, exemplos, códigos de resposta)
- Configuração de segurança para permitir acesso ao Swagger UI sem autenticação
- Configuração global da documentação (título, versão, descrição, esquema de autenticação Bearer)

## Capabilities

### New Capabilities
- `swagger-docs`: Documentação OpenAPI/Swagger para todos os endpoints da API, com UI interativa, esquema de autenticação Bearer configurado e exemplos de request/response

### Modified Capabilities

## Impact

- **Dependências**: Adição de `springdoc-openapi-starter-webmvc-ui`
- **Código**: Anotações `@Operation`, `@ApiResponse`, `@Tag` nos 4 controllers (AuthController, ServiceCatalogController, CalendarController, ReportController)
- **Segurança**: SecurityConfig precisa permitir acesso a `/swagger-ui/**` e `/v3/api-docs/**` sem autenticação
- **APIs**: Nenhuma alteração funcional nos endpoints existentes
