## ADDED Requirements

### Requirement: Optional startDate query parameter for manual calendar sync
The system SHALL accept an optional `startDate` query parameter in `POST /api/calendar/sync`, using ISO date format `yyyy-mm-dd`.

#### Scenario: Sync request with valid startDate
- **WHEN** an authenticated user calls `POST /api/calendar/sync?startDate=2026-04-01`
- **THEN** the API MUST accept the request and execute synchronization for that user

#### Scenario: Sync request without startDate keeps compatibility
- **WHEN** an authenticated user calls `POST /api/calendar/sync` without `startDate`
- **THEN** the API MUST execute synchronization using the current behavior, without requiring any new parameter

### Requirement: Sync with startDate filters Google events from the informed date
When `startDate` is provided, the system SHALL synchronize events starting from the informed date onward.

#### Scenario: Filtered sync from date boundary
- **WHEN** `startDate` is provided in the sync request
- **THEN** the Google Calendar fetch MUST apply the informed date as the lower bound for synchronized events

### Requirement: startDate sync must not overwrite the main incremental sync token
Synchronization triggered with `startDate` SHALL NOT replace the persisted incremental `syncToken` used by the default sync flow.

#### Scenario: Existing sync token remains unchanged after startDate sync
- **WHEN** a user already has a persisted incremental `syncToken` and executes `POST /api/calendar/sync` with `startDate`
- **THEN** the persisted incremental `syncToken` MUST remain unchanged after the filtered synchronization completes

#### Scenario: Subsequent default sync preserves current incremental semantics
- **WHEN** a startDate-based sync was executed previously and the user later calls `POST /api/calendar/sync` without `startDate`
- **THEN** the API MUST continue using the existing incremental/full/fallback semantics from the current implementation

### Requirement: HTTP contract remains stable with optional startDate
The endpoint SHALL keep the current response and error contracts regardless of `startDate` presence.

#### Scenario: Success response shape is unchanged
- **WHEN** sync completes successfully with or without `startDate`
- **THEN** the API MUST return HTTP `200` and the same payload shape with `created`, `updated`, and `deleted`

#### Scenario: Existing sync error mapping is unchanged
- **WHEN** integration revoked or Google Calendar API forbidden errors occur with or without `startDate`
- **THEN** the API MUST keep existing status and error code behavior for `INTEGRATION_REVOKED` and `GOOGLE_API_FORBIDDEN`
