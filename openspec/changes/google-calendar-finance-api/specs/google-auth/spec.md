## ADDED Requirements

### Requirement: Validate Google ID Token
The system SHALL validate the Google ID Token on every authenticated request, checking signature, `aud` (must match configured client ID), `iss` (must be accounts.google.com or https://accounts.google.com), and `exp` (must not be expired).

#### Scenario: Valid ID Token
- **WHEN** client sends a request with a valid, non-expired Google ID Token in the Authorization Bearer header
- **THEN** the request SHALL be authenticated and the user identity extracted from the token

#### Scenario: Expired ID Token
- **WHEN** client sends a request with an expired Google ID Token
- **THEN** the system SHALL reject the request with HTTP 401

#### Scenario: Invalid audience
- **WHEN** client sends a request with an ID Token whose `aud` does not match the configured client ID
- **THEN** the system SHALL reject the request with HTTP 401

#### Scenario: Invalid issuer
- **WHEN** client sends a request with an ID Token whose `iss` is not a valid Google issuer
- **THEN** the system SHALL reject the request with HTTP 401

#### Scenario: Missing Authorization header
- **WHEN** client sends a request to a protected endpoint without Authorization header
- **THEN** the system SHALL reject the request with HTTP 401

### Requirement: Standardized 401 error response
The system SHALL return HTTP 401 with a JSON body containing `status`, `code`, `message` and `timestamp` for all authentication failures, including missing token, invalid token, expired token, invalid audience and invalid issuer.

#### Scenario: Missing Authorization header returns 401 JSON
- **WHEN** client sends a request to a protected endpoint without Authorization header
- **THEN** the system SHALL return HTTP 401 with body `{"status": 401, "code": "UNAUTHORIZED", "message": "Authentication required. Provide a valid Google ID Token in the Authorization header.", "timestamp": "<ISO-8601>"}`

#### Scenario: Invalid token returns 401 JSON
- **WHEN** client sends a request with a malformed or invalid Google ID Token
- **THEN** the system SHALL return HTTP 401 with body containing `"status": 401` and `"code": "UNAUTHORIZED"`

#### Scenario: Expired token returns 401 JSON
- **WHEN** client sends a request with an expired Google ID Token
- **THEN** the system SHALL return HTTP 401 with body containing `"status": 401` and `"code": "UNAUTHORIZED"`

### Requirement: Resolve internal user from Google sub
The system SHALL resolve or create an internal user record from the `sub` claim of the validated Google ID Token. The `sub` is the stable external identity for the user.

#### Scenario: Existing user login
- **WHEN** a validated ID Token contains a `sub` that matches an existing user
- **THEN** the system SHALL resolve to the existing internal user

#### Scenario: New user first login
- **WHEN** a validated ID Token contains a `sub` that does not match any existing user
- **THEN** the system SHALL create a new internal user record with the google_sub, email, and name from the token

### Requirement: Exchange authorization code for OAuth tokens
The system SHALL exchange the `authorizationCode` provided by the client for Google OAuth `access_token` and `refresh_token`, and persist them associated with the user.

#### Scenario: Successful code exchange
- **WHEN** client sends a valid `authorizationCode` along with the `idToken` in the initial authentication request
- **THEN** the system SHALL exchange the code with Google, obtain `access_token` and `refresh_token`, and persist them for the user

#### Scenario: Invalid authorization code
- **WHEN** client sends an invalid or already-used `authorizationCode`
- **THEN** the system SHALL return an appropriate error indicating the code exchange failed

### Requirement: No application-issued tokens
The system SHALL NOT emit any proprietary tokens (JWT, session tokens, etc.). All subsequent authenticated requests SHALL use the Google ID Token as bearer.

#### Scenario: Authentication response
- **WHEN** the initial authentication is successful
- **THEN** the response SHALL NOT include any application-generated authentication token

### Requirement: User identity from security context only
The system SHALL obtain the user identity exclusively from the authenticated security context. The system SHALL NEVER accept user identifiers sent by the client as the source of truth for authorization.

#### Scenario: Client sends userId in request body
- **WHEN** client includes a userId field in the request body
- **THEN** the system SHALL ignore it and use only the userId from the authenticated security context

### Requirement: Clear invalid integration status on re-authentication
The system SHALL clear the REAUTH_REQUIRED status and restore the integration when a user with revoked integration completes a new authentication with valid idToken and authorizationCode.

#### Scenario: Re-authentication clears invalid status
- **WHEN** a user with REAUTH_REQUIRED status completes a new authentication with valid idToken and authorizationCode
- **THEN** the system SHALL exchange the new code for fresh OAuth tokens, clear the REAUTH_REQUIRED status, reset error fields, and mark the integration as SYNCED

#### Scenario: Normal login does not trigger automatic sync
- **WHEN** a user completes the authentication with both idToken and authorizationCode
- **THEN** the system SHALL NOT automatically trigger a calendar sync; the user must trigger sync explicitly

### Requirement: Login response
The system SHALL return a response with the user's internal ID, email, and name after successful authentication.

#### Scenario: Successful login response
- **WHEN** the initial authentication is successful
- **THEN** the system SHALL return a JSON response with `userId` (Long), `email` (String), and `name` (String)

### Requirement: Browser-based OAuth flow for testing
The system SHALL provide a browser-based OAuth flow for testing purposes, allowing developers to obtain a valid idToken via browser redirect.

#### Scenario: Initiate browser OAuth
- **WHEN** a GET request is made to the browser OAuth endpoint
- **THEN** the system SHALL redirect to Google's authorization endpoint with scopes `openid email profile https://www.googleapis.com/auth/calendar.readonly`, `access_type=offline`, and `prompt=consent`

#### Scenario: Browser OAuth callback
- **WHEN** Google redirects back with an authorization code
- **THEN** the system SHALL exchange the code for tokens, validate the ID token, create/update the user and OAuth credentials, and return the userId, email, name, and idToken
