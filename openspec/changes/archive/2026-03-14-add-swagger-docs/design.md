## Context

A API mei-organizadin possui 4 controllers com 10 endpoints implementados, autenticação via Google ID Token como Bearer, e nenhuma documentação interativa. O projeto usa Spring Boot 3.4.3.

## Goals / Non-Goals

**Goals:**
- Swagger UI acessível sem autenticação em `/swagger-ui.html`
- Documentação OpenAPI 3.0 gerada automaticamente a partir do código
- Descrições claras de cada endpoint, parâmetros e respostas
- Esquema de autenticação Bearer configurado no Swagger UI para testar endpoints protegidos

**Non-Goals:**
- Geração de clientes a partir do OpenAPI spec
- Versionamento de API via OpenAPI
- Documentação de endpoints internos ou actuator

## Decisions

### 1. Biblioteca: SpringDoc OpenAPI

**Decisão**: Usar `springdoc-openapi-starter-webmvc-ui` (versão 2.8.x).

**Alternativas consideradas**:
- Springfox → Descontinuado, não suporta Spring Boot 3.x
- Swagger Core manual → Mais trabalho, sem auto-discovery de endpoints

**Rationale**: SpringDoc é a biblioteca padrão para OpenAPI com Spring Boot 3. Auto-detecta endpoints, suporta Bean Validation e gera UI interativa.

### 2. Configuração via Bean OpenApiConfig

**Decisão**: Criar uma classe `@Configuration` com `@Bean OpenAPI` para definir título, versão, descrição e esquema de segurança Bearer.

**Rationale**: Centraliza a configuração em um único ponto. As anotações nos controllers complementam com detalhes específicos de cada endpoint.

### 3. SecurityConfig: permitir acesso público ao Swagger

**Decisão**: Adicionar `/swagger-ui/**`, `/v3/api-docs/**` e `/swagger-ui.html` aos `requestMatchers().permitAll()`.

**Rationale**: A documentação precisa ser acessível sem autenticação para ser útil.

## Risks / Trade-offs

**[Exposição da documentação em produção]** → Para o TCC é aceitável. Em produção real, o Swagger UI deveria ser desabilitado ou protegido por profile.
