## Why

O plano original da API (change `google-calendar-finance-api`) define a integração com Google OAuth e Calendar de forma genérica, sem referenciar as credenciais reais do projeto Google Cloud. As credenciais já existem (projeto `mei-organizadin`, client_id `201818722010-...`) e precisam ser incorporadas ao plano de desenvolvimento para que a configuração do Spring Boot, a validação do ID Token, a troca de authorization code e os testes de integração utilizem os valores corretos desde o início.

## What Changes

- Configuração do `application.yml` / `application.properties` com client_id e client_secret reais do projeto Google Cloud `mei-organizadin`
- Gerenciamento seguro do client_secret via variáveis de ambiente ou Spring profiles (não hardcoded no código-fonte)
- Validação do Google ID Token com `aud` fixado no client_id real: `201818722010-8fqjl9673qn5hn3g4c1gs52cj0ftbuh6.apps.googleusercontent.com`
- Troca de authorization code usando o client_id + client_secret reais contra o token_uri `https://oauth2.googleapis.com/token`
- Atualização das tasks do plano original para incluir passos de configuração de credenciais, profiles de ambiente e testes com valores reais
- Refatoração do design para definir a estratégia de gerenciamento de segredos

## Capabilities

### New Capabilities
- `google-credentials-config`: Configuração e gerenciamento seguro das credenciais OAuth do projeto Google Cloud (client_id, client_secret) via Spring Boot profiles e variáveis de ambiente

### Modified Capabilities

## Impact

- **Configuração**: `application.yml`, `application-dev.yml`, `application-test.yml` precisam das credenciais Google
- **Segurança**: client_secret não pode ser commitado no repositório — deve vir de variável de ambiente ou arquivo externo ao repo
- **Testes**: Testes de integração precisam de configuração adequada para validar ID Token com o client_id real
- **Dependências**: Nenhuma nova dependência — apenas configuração do que já existe no plano
- **Código**: GoogleIdTokenValidator e GoogleOAuthClient devem ler client_id/client_secret da configuração Spring, não de valores hardcoded
