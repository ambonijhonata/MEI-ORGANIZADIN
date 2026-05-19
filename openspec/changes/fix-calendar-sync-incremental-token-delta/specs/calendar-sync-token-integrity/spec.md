## ADDED Requirements

### Requirement: Incremental sync token MUST be persisted and reused across successful sync executions
The system SHALL persist a valid `nextSyncToken` after a successful synchronization and SHALL reuse that persisted token in subsequent sync requests for the same user.

#### Scenario: Successful full sync establishes incremental baseline
- **WHEN** a user executes sync without a previously persisted `syncToken` and Google returns a valid `nextSyncToken`
- **THEN** the system MUST persist that token and use it in the next sync request for the same user

#### Scenario: Successful incremental sync rotates token
- **WHEN** a user executes sync with an existing persisted `syncToken` and Google returns a new valid `nextSyncToken`
- **THEN** the system MUST replace the persisted token with the new token

### Requirement: Sync execution MUST NOT discard a previously valid token when the response has no new token
The system SHALL preserve the previously persisted `syncToken` when a sync execution completes without a valid `nextSyncToken`.

#### Scenario: Missing nextSyncToken preserves previous token
- **WHEN** sync starts with a non-empty persisted token and Google response does not include a valid `nextSyncToken`
- **THEN** the system MUST keep the previous persisted token unchanged and MUST log an operational warning

### Requirement: Full resync fallback MUST be explicit and observable
The system SHALL execute full resync only for explicit fallback conditions and SHALL annotate the execution mode in logs.

#### Scenario: Expired token triggers explicit full resync
- **WHEN** Google returns `410 Gone` for an incremental sync request
- **THEN** the system MUST clear the persisted token, execute full resync, and log fallback reason as token expiration

#### Scenario: Missing token triggers explicit full sync mode
- **WHEN** no persisted token exists for a user at sync start
- **THEN** the system MUST execute full sync mode and log fallback reason as missing token baseline
