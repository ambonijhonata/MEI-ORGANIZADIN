## ADDED Requirements

### Requirement: Detect OAuth revocation
The system SHALL detect when a user's Google OAuth refresh_token has been revoked, expired irrecoverably, or become invalid during synchronization or token refresh attempts.

#### Scenario: Refresh token revoked during sync
- **WHEN** the system attempts to refresh the access_token and Google responds with an authorization error indicating revocation
- **THEN** the system SHALL mark the user's integration as invalid (REAUTH_REQUIRED)

#### Scenario: Token refresh fails during calendar API call
- **WHEN** a Google Calendar API call fails due to invalid/revoked credentials
- **THEN** the system SHALL mark the integration as invalid and record the error

### Requirement: Mark integration as invalid on revocation
When OAuth revocation or irrecoverable token failure is detected, the system SHALL mark the user's Google integration status as invalid or REAUTH_REQUIRED, recording the failure timestamp and reason.

#### Scenario: Integration status updated
- **WHEN** an OAuth revocation is detected
- **THEN** the system SHALL set the integration status to REAUTH_REQUIRED with the failure timestamp and error category

### Requirement: Stop automatic sync after revocation
The system SHALL stop all automatic/periodic synchronization attempts for a user whose integration is marked as invalid.

#### Scenario: No auto-sync after revocation
- **WHEN** the integration is marked as REAUTH_REQUIRED
- **THEN** the system SHALL NOT attempt any automatic synchronization for that user until re-authentication

### Requirement: Preserve local data after revocation
The system SHALL preserve all previously synchronized data (events, service associations, value snapshots) when an integration is revoked. Local data SHALL NOT be deleted.

#### Scenario: Local data retained
- **WHEN** the integration is marked as invalid
- **THEN** all previously synchronized events and their associations SHALL remain in the database and be accessible for reads and reports

### Requirement: Require re-authentication to restore sync
The system SHALL require the user to perform a new full authentication flow (idToken + authorizationCode) to restore the Google integration and re-enable synchronization.

#### Scenario: Re-authentication restores integration
- **WHEN** a user with REAUTH_REQUIRED status completes a new authentication with valid idToken and authorizationCode
- **THEN** the system SHALL exchange the new code for fresh OAuth tokens, clear the invalid status, and re-enable synchronization

### Requirement: Expose integration status
The system SHALL provide an authenticated endpoint for querying the current state of the user's Google integration, including whether re-authentication is needed.

#### Scenario: Query integration status — active
- **WHEN** an authenticated user queries their integration status and the integration is active
- **THEN** the system SHALL return status ACTIVE with last sync timestamp

#### Scenario: Query integration status — reauth required
- **WHEN** an authenticated user queries their integration status and re-authentication is needed
- **THEN** the system SHALL return status REAUTH_REQUIRED with the failure timestamp and reason

### Requirement: Distinguish transient from permanent failures
The system SHALL distinguish transient failures (network issues, temporary Google errors) from permanent authorization failures (revoked tokens). Transient failures SHALL be retried with controlled backoff; permanent failures SHALL mark the integration as invalid.

#### Scenario: Transient failure retried
- **WHEN** a sync fails due to a transient error (e.g., 503 from Google)
- **THEN** the system SHALL retry with exponential backoff without marking the integration as invalid

#### Scenario: Permanent failure stops sync
- **WHEN** a sync fails due to a permanent authorization error (e.g., 401 with invalid_grant)
- **THEN** the system SHALL mark the integration as REAUTH_REQUIRED and stop retries
