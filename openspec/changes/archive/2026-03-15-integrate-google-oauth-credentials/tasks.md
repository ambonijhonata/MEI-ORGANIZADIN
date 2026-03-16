## 1. Configuração de Credenciais Google OAuth

- [ ] 1.1 Criar `GoogleOAuthProperties` record com `@ConfigurationProperties(prefix = "google.oauth")` contendo clientId, clientSecret, tokenUri, authUri
- [ ] 1.2 Adicionar `@EnableConfigurationProperties(GoogleOAuthProperties.class)` na classe principal ou configuração
- [ ] 1.3 Configurar `application.yml` com placeholders de variáveis de ambiente para credenciais sensíveis:
  ```yaml
  google:
    oauth:
      client-id: ${GOOGLE_CLIENT_ID}
      client-secret: ${GOOGLE_CLIENT_SECRET}
      token-uri: https://oauth2.googleapis.com/token
      auth-uri: https://accounts.google.com/o/oauth2/auth
  ```
- [ ] 1.4 Criar `application-dev.yml` com credenciais reais do projeto `mei-organizadin` (client_id: `201818722010-8fqjl9673qn5hn3g4c1gs52cj0ftbuh6.apps.googleusercontent.com`, client_secret: `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`)
- [ ] 1.5 Adicionar `application-dev.yml` ao `.gitignore`
- [ ] 1.6 Criar `application-dev.yml.example` no repositório com placeholders documentados
- [ ] 1.7 Criar `application-test.yml` com client_id real e client_secret mockado para testes de integração

## 2. Validação de Startup e Testes de Configuração

- [ ] 2.1 Escrever teste unitário: aplicação falha ao iniciar sem `google.oauth.client-id`
- [ ] 2.2 Escrever teste unitário: aplicação falha ao iniciar sem `google.oauth.client-secret`
- [ ] 2.3 Escrever teste unitário: `GoogleOAuthProperties` carrega todos os valores corretamente do application.yml
- [ ] 2.4 Adicionar `@Validated` e `@NotBlank` nos campos de `GoogleOAuthProperties` para validação no startup

## 3. Refatorar GoogleIdTokenValidator para Usar Configuração

- [ ] 3.1 Escrever teste unitário: ID Token com `aud` igual ao `clientId` configurado é aceito
- [ ] 3.2 Escrever teste unitário: ID Token com `aud` diferente do `clientId` configurado é rejeitado com 401
- [ ] 3.3 Refatorar `GoogleIdTokenValidator` para receber `GoogleOAuthProperties` por injeção e usar `properties.clientId()` como audience esperada no `GoogleIdTokenVerifier`
- [ ] 3.4 Remover qualquer client_id hardcoded do código do validator

## 4. Refatorar GoogleOAuthClient para Usar Configuração

- [ ] 4.1 Escrever teste unitário: troca de authorization code usa clientId e clientSecret da configuração
- [ ] 4.2 Refatorar `GoogleOAuthClient` para receber `GoogleOAuthProperties` por injeção
- [ ] 4.3 Usar `properties.clientId()`, `properties.clientSecret()` e `properties.tokenUri()` na construção do `GoogleAuthorizationCodeTokenRequest`
- [ ] 4.4 Remover qualquer credencial hardcoded do código do OAuth client

## 5. Segurança de Credenciais

- [ ] 5.1 Verificar que `.gitignore` inclui `application-dev.yml` e qualquer arquivo `*secret*` ou `*credential*`
- [ ] 5.2 Escrever teste ou verificação: logs de startup não contêm o valor do client_secret
- [ ] 5.3 Garantir que `GoogleOAuthProperties` não implementa `toString()` que exponha o client_secret (usar record sem override ou override seguro)

## 6. Atualização do Plano Original (google-calendar-finance-api)

- [ ] 6.1 Atualizar task 1.1 do plano original: incluir `google-api-client` e `google-oauth-client` como dependências Maven para `GoogleIdTokenVerifier` e `GoogleAuthorizationCodeTokenRequest`
- [ ] 6.2 Atualizar task 2.2 do plano original: `GoogleIdTokenValidator` deve usar `GoogleOAuthProperties.clientId()` como audience, não valor hardcoded
- [ ] 6.3 Atualizar task 2.10 do plano original: `GoogleOAuthClient` deve usar `GoogleOAuthProperties` para clientId, clientSecret e tokenUri
- [ ] 6.4 Atualizar task 2.13 do plano original: testes de integração de autenticação devem usar `application-test.yml` com client_id real
- [ ] 6.5 Atualizar task 4.6 do plano original: `GoogleCalendarClient` deve obter credenciais OAuth do usuário do banco e usar `GoogleOAuthProperties.clientId()` para refresh de access_token

## 7. Testes de Integração com Credenciais Reais

- [ ] 7.1 Escrever teste de integração: aplicação Spring Boot inicia corretamente com profile `test` e credenciais configuradas
- [ ] 7.2 Escrever teste de integração: endpoint de autenticação valida ID Token com `aud` do client_id real do projeto `mei-organizadin`
- [ ] 7.3 Escrever teste de integração: endpoint de autenticação rejeita ID Token com `aud` de outro projeto Google
