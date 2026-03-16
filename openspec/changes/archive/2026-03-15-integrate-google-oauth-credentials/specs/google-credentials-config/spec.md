## ADDED Requirements

### Requirement: Load Google OAuth credentials from Spring configuration
The system SHALL load Google OAuth client_id, client_secret, token_uri, and auth_uri from Spring Boot configuration properties under the prefix `google.oauth`, using a typed `@ConfigurationProperties` class.

#### Scenario: Application starts with all properties set
- **WHEN** the application starts with `google.oauth.client-id`, `google.oauth.client-secret`, `google.oauth.token-uri` all configured
- **THEN** the `GoogleOAuthProperties` bean SHALL be created with all values populated

#### Scenario: Application fails to start with missing client-id
- **WHEN** the application starts without `google.oauth.client-id` configured
- **THEN** the application SHALL fail to start with a configuration validation error

#### Scenario: Application fails to start with missing client-secret
- **WHEN** the application starts without `google.oauth.client-secret` configured
- **THEN** the application SHALL fail to start with a configuration validation error

### Requirement: Validate ID Token audience against configured client_id
The GoogleIdTokenValidator SHALL use the `client-id` from `GoogleOAuthProperties` as the expected `aud` value when validating Google ID Tokens. The client_id used SHALL be `201818722010-8fqjl9673qn5hn3g4c1gs52cj0ftbuh6.apps.googleusercontent.com` (project `mei-organizadin`).

#### Scenario: ID Token with correct audience accepted
- **WHEN** a Google ID Token is validated and its `aud` claim matches the configured `google.oauth.client-id`
- **THEN** the token SHALL pass audience validation

#### Scenario: ID Token with wrong audience rejected
- **WHEN** a Google ID Token is validated and its `aud` claim does not match the configured `google.oauth.client-id`
- **THEN** the token SHALL be rejected with HTTP 401

### Requirement: Exchange authorization code using configured credentials
The GoogleOAuthClient SHALL use `client-id`, `client-secret`, and `token-uri` from `GoogleOAuthProperties` to exchange the authorization code for access_token and refresh_token.

#### Scenario: Successful code exchange with real credentials
- **WHEN** the backend exchanges an authorization code with Google
- **THEN** the request SHALL include the configured client_id and client_secret in the token request to the configured token_uri

### Requirement: Support environment variable overrides
The application.yml SHALL reference environment variables for sensitive values (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`) so that credentials can be injected at runtime without being hardcoded in committed files.

#### Scenario: Environment variables override defaults
- **WHEN** environment variables `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are set
- **THEN** the application SHALL use those values for the Google OAuth configuration

### Requirement: Dev profile with local credentials
The system SHALL support a `dev` Spring profile with an `application-dev.yml` file that contains the real credential values for local development. This file SHALL be listed in `.gitignore` to prevent accidental commits.

#### Scenario: Dev profile activates local credentials
- **WHEN** the application is started with `--spring.profiles.active=dev`
- **THEN** the Google OAuth properties SHALL be loaded from `application-dev.yml`

#### Scenario: Dev profile file not committed
- **WHEN** a developer checks the `.gitignore` file
- **THEN** `application-dev.yml` SHALL be listed as an ignored file

### Requirement: Example configuration file in repository
The system SHALL include an `application-dev.yml.example` file in the repository with placeholder values, documenting which properties need to be configured.

#### Scenario: Example file exists with placeholders
- **WHEN** a developer clones the repository
- **THEN** they SHALL find `application-dev.yml.example` with placeholder values for `google.oauth.client-id` and `google.oauth.client-secret`

### Requirement: Client secret never in application logs
The system SHALL NEVER log the `client_secret` value. Log configuration SHALL ensure that the Google OAuth properties are not inadvertently printed during startup or error handling.

#### Scenario: Startup logs do not contain secret
- **WHEN** the application starts and logs configuration
- **THEN** the log output SHALL NOT contain the client_secret value
