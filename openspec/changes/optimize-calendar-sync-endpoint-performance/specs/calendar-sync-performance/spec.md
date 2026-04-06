## ADDED Requirements

### Requirement: Preserve Sync Endpoint Behavioral Contract
The system SHALL preserve the current functional behavior of `POST /api/calendar/sync` while implementing performance optimizations.

#### Scenario: Successful sync response remains compatible
- **WHEN** a user triggers `POST /api/calendar/sync` and synchronization completes
- **THEN** the API MUST return HTTP `200` with the same response shape and semantics for `created`, `updated`, and `deleted`

#### Scenario: Integration revoked error remains compatible
- **WHEN** the sync flow detects revoked Google integration
- **THEN** the API MUST return HTTP `403` with error code `INTEGRATION_REVOKED` using the existing error payload contract

#### Scenario: Google API forbidden error remains compatible
- **WHEN** the sync flow detects Google API access denied/forbidden
- **THEN** the API MUST return HTTP `403` with error code `GOOGLE_API_FORBIDDEN` using the existing error payload contract

#### Scenario: Expired sync token still triggers full resync
- **WHEN** Google Calendar API returns token expiration for an incremental sync attempt
- **THEN** the system MUST execute full resync fallback and complete with the same functional semantics currently in use

### Requirement: Reduce Database Round-Trips During High-Volume Sync
The system SHALL minimize repeated database lookups and writes in `POST /api/calendar/sync` by using batch-oriented processing while preserving existing matching and persistence rules.

#### Scenario: Event existence lookup is optimized for bulk input
- **WHEN** sync processes a large event set for a user
- **THEN** the implementation MUST avoid per-event existence queries as the primary lookup strategy and MUST use bulk preloading/caching by `googleEventId`

#### Scenario: Client and service matching are optimized for bulk input
- **WHEN** sync resolves clients and services for multiple events in the same execution
- **THEN** the implementation MUST avoid repeated equivalent repository queries per event by using execution-scoped lookup caches or equivalent bulk strategy

#### Scenario: Persistence is performed in batches
- **WHEN** sync persists a large number of created or updated events
- **THEN** the implementation MUST use batched persistence strategy to reduce per-row write overhead while preserving data correctness

### Requirement: Optimize Google Calendar Fetch Overhead
The system SHALL reduce Google Calendar fetch overhead in `POST /api/calendar/sync` without changing incremental/full sync semantics.

#### Scenario: Fetch requests use payload and paging optimizations
- **WHEN** sync requests events from Google Calendar
- **THEN** the implementation MUST apply paging and field-selection optimizations that reduce request count and transferred payload while preserving required event data

#### Scenario: Fetch optimization preserves existing semantics
- **WHEN** fetch optimization is enabled
- **THEN** incremental sync behavior, pagination completeness, and `nextSyncToken` handling MUST remain functionally equivalent to current behavior

### Requirement: Provide Sync Stage Observability
The system SHALL expose stage-level observability for `POST /api/calendar/sync` to support diagnosis and regression detection.

#### Scenario: Stage timings and counters are emitted
- **WHEN** a sync execution finishes (success or failure)
- **THEN** the system MUST record structured telemetry that includes total duration, stage durations, processed event volume, and result counters

#### Scenario: Full resync fallback is traceable
- **WHEN** incremental sync falls back to full resync
- **THEN** the system MUST emit explicit telemetry indicating fallback reason and execution path

### Requirement: Prevent Redundant Concurrent Sync Work Per User
The system SHALL prevent redundant concurrent processing of multiple `POST /api/calendar/sync` executions for the same user.

#### Scenario: Concurrent sync requests for the same user are coordinated
- **WHEN** two or more sync requests for the same `userId` overlap in time
- **THEN** the system MUST coordinate execution to avoid duplicated heavy processing for that user while preserving endpoint response compatibility

#### Scenario: Different users remain independent
- **WHEN** sync requests are executed concurrently for different users
- **THEN** the system MUST allow independent processing without global serialization across all users
