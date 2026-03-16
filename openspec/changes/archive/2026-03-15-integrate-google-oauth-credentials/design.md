## Context

O plano original (`google-calendar-finance-api`) define autenticação via Google ID Token e integração com Google Calendar, mas trata client_id e client_secret como valores genéricos a configurar. As credenciais reais já existem no projeto Google Cloud `mei-organizadin` e estão no arquivo `client_secret_201818722010-...json` com tipo `web`.

Dados concretos disponíveis:
- **client_id**: `201818722010-8fqjl9673qn5hn3g4c1gs52cj0ftbuh6.apps.googleusercontent.com`
- **project_id**: `mei-organizadin`
- **client_secret**: `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`
- **token_uri**: `https://oauth2.googleapis.com/token`
- **auth_uri**: `https://accounts.google.com/o/oauth2/auth`
- **cert_url**: `https://www.googleapis.com/oauth2/v1/certs`

O tipo de credencial é `web`, o que é correto para um backend server-side que recebe authorization codes do app Android.

## Goals / Non-Goals

**Goals:**
- Definir como as credenciais Google Cloud reais são carregadas pelo Spring Boot
- Garantir que o client_secret nunca seja commitado diretamente no código-fonte do repositório
- Configurar profiles separados para dev, test e produção
- Usar o client_id real como `aud` esperado na validação do Google ID Token
- Usar client_id + client_secret reais na troca de authorization code por tokens OAuth
- Atualizar o plano de tasks para incluir as etapas de configuração de credenciais

**Non-Goals:**
- Implementar vault ou KMS — para o escopo do TCC, variáveis de ambiente são suficientes
- Rotação automática de credenciais
- Múltiplos projetos Google Cloud

## Decisions

### 1. Estratégia de configuração: Spring Boot properties com variáveis de ambiente

**Decisão**: Criar propriedades customizadas em `application.yml` que referenciam variáveis de ambiente para valores sensíveis.

```yaml
google:
  oauth:
    client-id: ${GOOGLE_CLIENT_ID}
    client-secret: ${GOOGLE_CLIENT_SECRET}
    token-uri: https://oauth2.googleapis.com/token
    auth-uri: https://accounts.google.com/o/oauth2/auth
```

**Alternativas consideradas**:
- Hardcoded no `application.yml` → Risco de vazamento se commitado no repositório
- Arquivo `.properties` externo → Mais complexo de gerenciar sem benefício para TCC
- Spring Cloud Config → Overengineering para o escopo

**Rationale**: Variáveis de ambiente são o padrão do 12-Factor App, simples de configurar em dev (IDE/terminal) e em produção (container/cloud). O `application.yml` contém os placeholders, os valores reais vêm do ambiente.

### 2. Profile de desenvolvimento com valores locais

**Decisão**: Criar `application-dev.yml` com os valores reais para facilitar o desenvolvimento local. Este arquivo será adicionado ao `.gitignore`.

```yaml
# application-dev.yml (NÃO commitado)
google:
  oauth:
    client-id: 201818722010-8fqjl9673qn5hn3g4c1gs52cj0ftbuh6.apps.googleusercontent.com
    client-secret: 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
```

**Rationale**: Permite rodar a aplicação localmente com `--spring.profiles.active=dev` sem precisar exportar variáveis de ambiente a cada vez. O `.gitignore` protege contra commit acidental.

### 3. Profile de testes

**Decisão**: Criar `application-test.yml` com client_id real para validação de `aud` nos testes, mas client_secret mockado onde necessário.

**Rationale**: Testes de integração que validam o fluxo de autenticação precisam do client_id real para que a verificação de `aud` no ID Token funcione corretamente. A troca de authorization code pode ser mockada nos testes de integração.

### 4. Configuration class tipada

**Decisão**: Criar `GoogleOAuthProperties` com `@ConfigurationProperties(prefix = "google.oauth")` para injeção tipada.

```java
@ConfigurationProperties(prefix = "google.oauth")
public record GoogleOAuthProperties(
    String clientId,
    String clientSecret,
    String tokenUri,
    String authUri
) {}
```

**Rationale**: Tipagem forte, validação no startup, auto-complete na IDE, e centraliza a configuração em um único ponto. Todos os componentes (GoogleIdTokenValidator, GoogleOAuthClient) recebem esta classe por injeção.

### 5. Uso concreto das credenciais nos componentes

**GoogleIdTokenValidator**: Recebe `GoogleOAuthProperties` e usa `clientId` como `aud` esperado na validação do ID Token via Google API Client Library (`GoogleIdTokenVerifier.Builder` com `setAudience`).

**GoogleOAuthClient**: Usa `clientId`, `clientSecret` e `tokenUri` para montar a requisição de troca do authorization code por access_token + refresh_token (`GoogleAuthorizationCodeTokenRequest`).

### 6. Arquivo de exemplo para o repositório

**Decisão**: Criar `application-dev.yml.example` no repositório com placeholders, para documentar quais variáveis são necessárias.

**Rationale**: Permite que qualquer desenvolvedor saiba quais valores configurar sem expor os segredos reais.

## Risks / Trade-offs

**[Client secret no application-dev.yml local]** → Se o `.gitignore` falhar, o secret pode ser commitado. Mitigação: adicionar `application-dev.yml` ao `.gitignore` logo na criação do projeto; adicionar verificação no pre-commit hook se possível.

**[Client secret tipo `web` usado em backend]** → O tipo `web` é o correto para server-side. Não há risco aqui — o backend recebe o authorization code do app Android e faz a troca server-to-server com o Google.

**[Credencial única para todos os ambientes]** → Para o TCC, uma credencial é suficiente. Em produção real, seria ideal ter credenciais separadas por ambiente.
