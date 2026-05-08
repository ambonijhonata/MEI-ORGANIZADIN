## ADDED Requirements

### Requirement: Synchronization SHALL consume pending late-service enrichment
The system SHALL persist and consume pending late-service enrichment for a user during calendar synchronization so that already synchronized appointments can gain newly matchable services even when Google returns no event delta for those appointments.

#### Scenario: Manual sync repairs legacy partially identified appointments
- **WHEN** a user has synchronized appointments whose saved titles contain services that were unavailable during the original synchronization
- **AND** the user has a pending late-service enrichment state
- **AND** the user triggers calendar synchronization
- **THEN** the system SHALL run local appointment enrichment for that user during the synchronization flow
- **AND** appointments that can gain new service associations SHALL be enriched even if Google returned no changed event for them

#### Scenario: Async enrichment fails and later sync retries
- **WHEN** a catalog change marks a user's enrichment state as pending
- **AND** the immediate asynchronous enrichment attempt does not complete successfully
- **THEN** the pending state SHALL remain persisted
- **AND** a later synchronization SHALL retry the enrichment before clearing the pending state

### Requirement: Legacy Google-backed users SHALL receive one-time enrichment backfill
The system SHALL include a one-time backfill path that causes existing users with already synchronized Google-backed appointments to become eligible for late-service enrichment after deployment of this change.

#### Scenario: Existing synchronized user is marked for enrichment after deploy
- **WHEN** this change is deployed to an environment that already contains synchronized Google-backed appointments
- **THEN** users with such appointments SHALL be marked for at least one late-service enrichment pass
- **AND** a subsequent synchronization or enrichment execution SHALL re-evaluate their saved appointment titles against the current catalog
