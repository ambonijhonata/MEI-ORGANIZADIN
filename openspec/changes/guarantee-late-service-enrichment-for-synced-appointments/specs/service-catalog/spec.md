## ADDED Requirements

### Requirement: Catalog changes SHALL persist reliable late-service enrichment retries
Service catalog changes that can create new title matches SHALL persist a retryable enrichment signal for the affected user instead of relying solely on a single best-effort asynchronous execution.

#### Scenario: Service creation persists enrichment pending state
- **WHEN** an authenticated user creates a new service
- **AND** that service could match previously synchronized appointment titles
- **THEN** the system SHALL persist a late-service enrichment pending state or requested revision for that user
- **AND** the system SHALL attempt asynchronous enrichment without requiring a Google resynchronization

#### Scenario: Service description update persists enrichment pending state
- **WHEN** an authenticated user updates a service description so its normalized form changes
- **THEN** the system SHALL persist a late-service enrichment pending state or requested revision for that user
- **AND** the system SHALL attempt asynchronous enrichment using the updated catalog

### Requirement: Value-only updates SHALL NOT trigger Google-based repair
Updating only the monetary value of a service SHALL NOT require or trigger a Google synchronization to repair synchronized appointments.

#### Scenario: Value-only change keeps repair local and deferred-free
- **WHEN** an authenticated user updates only the value of an existing service
- **THEN** the system SHALL NOT trigger a full or incremental Google synchronization as part of the repair mechanism
- **AND** historical appointment snapshots SHALL remain unchanged unless some other catalog change later makes a new service match possible
