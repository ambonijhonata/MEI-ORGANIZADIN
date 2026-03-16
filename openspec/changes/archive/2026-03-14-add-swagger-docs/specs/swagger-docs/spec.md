## ADDED Requirements

### Requirement: Swagger UI disponível sem autenticação
O sistema SHALL disponibilizar a interface Swagger UI em `/swagger-ui.html` sem exigir autenticação Bearer.

#### Scenario: Acesso ao Swagger UI
- **WHEN** um usuário acessa `/swagger-ui.html` no navegador sem header de autenticação
- **THEN** o sistema retorna a página do Swagger UI com a documentação de todos os endpoints

### Requirement: Documentação OpenAPI gerada automaticamente
O sistema SHALL gerar a especificação OpenAPI 3.0 em `/v3/api-docs` contendo todos os endpoints públicos e autenticados da API.

#### Scenario: Acesso ao JSON OpenAPI
- **WHEN** um cliente faz GET em `/v3/api-docs`
- **THEN** o sistema retorna um JSON com a especificação OpenAPI 3.0 incluindo todos os endpoints, parâmetros e schemas de resposta

### Requirement: Esquema de autenticação Bearer no Swagger UI
O sistema SHALL configurar o esquema de autenticação Bearer (Google ID Token) na interface Swagger UI, permitindo que o usuário insira o token e teste endpoints protegidos.

#### Scenario: Testar endpoint protegido via Swagger UI
- **WHEN** o usuário insere um Google ID Token válido no botão "Authorize" do Swagger UI e faz uma requisição a um endpoint protegido
- **THEN** o Swagger UI envia o header `Authorization: Bearer <token>` e exibe a resposta do endpoint

### Requirement: Endpoints documentados com descrições
Cada endpoint da API SHALL ter descrição do propósito, parâmetros documentados e códigos de resposta possíveis na documentação OpenAPI.

#### Scenario: Visualizar documentação de endpoint
- **WHEN** o usuário expande um endpoint no Swagger UI
- **THEN** o sistema exibe a descrição da operação, parâmetros com tipos e exemplos, e os códigos de resposta HTTP possíveis (200, 400, 401, 404, etc.)
