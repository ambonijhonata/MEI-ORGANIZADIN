## ADDED Requirements

### Requirement: Calendar sync mutation phases MUST use explicit short-lived transactions
The system SHALL execute each database mutation phase of `POST /api/calendar/sync` inside an explicit active transaction that excludes external Google Calendar I/O.

#### Scenario: Google fetch stays outside the database transaction
- **WHEN** sync requests events from Google Calendar
- **THEN** that external fetch MUST complete before opening the transaction used for JPA mutations of the corresponding chunk

#### Scenario: Chunk mutation keeps transaction integrity
- **WHEN** sync processes a chunk containing existing events to update, new events to insert, and stale events to delete
- **THEN** lookup of mutable existing rows, service-association mutation, persistence, cleanup, and flush for that chunk MUST execute with an active transaction

### Requirement: Reconciliation paths MUST preserve transaction and error integrity
The system SHALL preserve correct sync-state and persistence integrity for `incremental`, `full sync`, and `startDate sync` reconciliation paths after the refactor.

#### Scenario: startDate sync uses the same safe mutation model
- **WHEN** sync runs with a `startDate` filter and needs to update or delete local rows within the bounded scope
- **THEN** those mutations MUST execute inside the same explicit transaction model used by the other sync paths
- **AND** the endpoint MUST preserve its existing success payload semantics

#### Scenario: Internal persistence failure marks sync as failed
- **WHEN** an unexpected runtime persistence error occurs during a sync mutation phase
- **THEN** sync state MUST be persisted as failed with internal error classification before the request error response is returned
