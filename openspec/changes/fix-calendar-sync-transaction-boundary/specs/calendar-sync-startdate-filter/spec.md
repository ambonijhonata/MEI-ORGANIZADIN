## MODIFIED Requirements

### Requirement: Optional startDate query parameter for manual calendar sync
The system SHALL accept an optional `startDate` query parameter in `POST /api/calendar/sync`, using ISO date format `yyyy-mm-dd`.

#### Scenario: Sync request with valid startDate
- **WHEN** an authenticated user calls `POST /api/calendar/sync?startDate=2026-04-01`
- **THEN** the API MUST accept the request and execute synchronization for that user
- **THEN** all persistence mutation and flush steps executed by this synchronization MUST run inside an active transaction context

#### Scenario: Sync request without startDate keeps compatibility
- **WHEN** an authenticated user calls `POST /api/calendar/sync` without `startDate`
- **THEN** the API MUST execute synchronization using the current behavior, without requiring any new parameter
- **THEN** all persistence mutation and flush steps executed by this synchronization MUST run inside an active transaction context

### Requirement: HTTP contract remains stable with optional startDate
The endpoint SHALL keep the current response and error contracts regardless of `startDate` presence.

#### Scenario: Success response shape is unchanged
- **WHEN** sync completes successfully with or without `startDate`
- **THEN** the API MUST return HTTP `200` and the same payload shape with `created`, `updated`, and `deleted`

#### Scenario: Existing sync error mapping is unchanged
- **WHEN** integration revoked or Google Calendar API forbidden errors occur with or without `startDate`
- **THEN** the API MUST keep existing status and error code behavior for `INTEGRATION_REVOKED` and `GOOGLE_API_FORBIDDEN`

#### Scenario: Internal persistence failure remains application error
- **WHEN** an internal runtime persistence failure occurs with or without `startDate`
- **THEN** the API MUST return an application error classification and MUST NOT mask the failure as `UNAUTHORIZED`
