## MODIFIED Requirements

### Requirement: Sync with startDate filters Google events from the informed date
When `startDate` is provided, the system SHALL synchronize events starting from the informed date onward, without invalidating the user's incremental sync baseline.

#### Scenario: Filtered sync from date boundary
- **WHEN** `startDate` is provided in the sync request
- **THEN** the Google Calendar fetch MUST apply the informed date as the lower bound for synchronized events

#### Scenario: Filtered sync keeps incremental baseline eligible
- **WHEN** `startDate` sync completes for a user
- **THEN** the next default sync without `startDate` MUST continue eligible for incremental execution using the persisted baseline token semantics

### Requirement: startDate sync must not overwrite the main incremental sync token
Synchronization triggered with `startDate` SHALL NOT replace a valid persisted incremental `syncToken` with null/blank value and SHALL preserve the default incremental flow.

#### Scenario: Existing sync token remains unchanged after startDate sync without new token
- **WHEN** a user has a persisted incremental `syncToken` and executes `POST /api/calendar/sync` with `startDate` and no valid new token is returned
- **THEN** the persisted incremental `syncToken` MUST remain unchanged after the filtered synchronization completes

#### Scenario: Existing sync token may rotate after startDate sync with new token
- **WHEN** a user has a persisted incremental `syncToken` and executes `POST /api/calendar/sync` with `startDate` and a valid new token is returned
- **THEN** the persisted incremental `syncToken` MUST be updated to the new token

#### Scenario: Subsequent default sync preserves current incremental semantics
- **WHEN** a startDate-based sync was executed previously and the user later calls `POST /api/calendar/sync` without `startDate`
- **THEN** the API MUST continue using incremental/full/fallback semantics based on the persisted token state
