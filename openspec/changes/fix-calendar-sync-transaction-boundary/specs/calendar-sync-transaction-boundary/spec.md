## ADDED Requirements

### Requirement: Calendar sync persistence MUST run inside an active transaction
The system SHALL execute all database mutation and flush operations of `POST /api/calendar/sync` inside an active JPA transaction, including cleanup and bulk deletion paths.

#### Scenario: Initial full sync cleanup performs delete and flush with active transaction
- **WHEN** sync enters initial full-sync reconciliation and needs to delete stale local events
- **THEN** bulk delete and flush operations MUST execute with an active transaction and MUST NOT throw `TransactionRequiredException`

#### Scenario: Incremental sync deletion path keeps transaction integrity
- **WHEN** incremental sync processes cancelled Google events that require local deletions
- **THEN** payment cleanup, event deletion, and subsequent flush MUST run in the same active transaction context

### Requirement: Transaction failure MUST be surfaced as internal sync failure state
If an unexpected runtime persistence error occurs during sync, the system MUST persist failed sync state before returning the application error response.

#### Scenario: Persistence runtime error updates sync state before response
- **WHEN** a runtime persistence error happens during sync mutation
- **THEN** sync state MUST be persisted as failed with an internal error category and the request MUST return an application error (not authentication error)
