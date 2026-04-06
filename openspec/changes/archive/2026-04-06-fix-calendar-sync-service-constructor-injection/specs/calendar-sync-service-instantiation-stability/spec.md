## ADDED Requirements

### Requirement: Calendar Sync Service Bean Must Initialize Deterministically
The system SHALL instantiate `CalendarSyncService` deterministically in Spring runtime without requiring a default constructor.

#### Scenario: Application context startup succeeds
- **WHEN** the API application starts with normal runtime configuration
- **THEN** Spring MUST create `CalendarSyncService` successfully and complete context initialization

#### Scenario: Constructor ambiguity is prevented
- **WHEN** Spring resolves dependencies for `CalendarSyncService`
- **THEN** the production constructor selection MUST be explicit and MUST NOT depend on ambiguous constructor heuristics

### Requirement: Fix Must Preserve Existing API Behavior
The system SHALL preserve current endpoint behavior while applying constructor-instantiation fixes.

#### Scenario: Sync success contract remains unchanged
- **WHEN** `POST /api/calendar/sync` completes successfully after the fix
- **THEN** the API MUST keep HTTP `200` with the same response shape and counter semantics (`created`, `updated`, `deleted`)

#### Scenario: Sync error contracts remain unchanged
- **WHEN** sync errors occur after the fix
- **THEN** the API MUST keep existing HTTP status and error code behavior (including `INTEGRATION_REVOKED` and `GOOGLE_API_FORBIDDEN`)

#### Scenario: Sync-token fallback remains unchanged
- **WHEN** incremental sync receives token-expired response from Google
- **THEN** the API MUST continue executing full resync fallback with the same current semantics
