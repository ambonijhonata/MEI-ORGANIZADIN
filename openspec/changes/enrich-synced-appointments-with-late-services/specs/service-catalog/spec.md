## ADDED Requirements

### Requirement: Service catalog changes SHALL trigger appointment enrichment
Service creation and service description updates SHALL trigger asynchronous enrichment of synchronized appointments that can gain new service associations from the catalog change. The trigger SHALL not depend on the appointment still being marked as unidentified.

#### Scenario: Service creation re-evaluates partially identified appointments
- **WHEN** an authenticated user creates a new service
- **AND** the user's synchronized appointments include titles with previously unmatched service tokens
- **THEN** the system SHALL asynchronously re-evaluate those synchronized appointments against the updated catalog
- **AND** appointments that can gain new associations SHALL be enriched without waiting for a new Google-side event change

#### Scenario: Service description update enables a new match
- **WHEN** an authenticated user updates a service description so its normalized form matches a previously unmatched token in a synchronized appointment title
- **THEN** the system SHALL asynchronously enrich the existing synchronized appointment with that service
- **AND** already valid service associations on the appointment SHALL be preserved

#### Scenario: Value-only update does not rewrite existing snapshots
- **WHEN** an authenticated user updates only the monetary value of a service that is already linked to synchronized appointments
- **THEN** the system SHALL NOT rewrite historical snapshots already stored on those appointments as part of the catalog-change enrichment flow
