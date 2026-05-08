## MODIFIED Requirements

### Requirement: Persisted service-link replacement SHALL use one canonical removal path
The system SHALL replace the service associations of an already persisted calendar event through a single canonical removal path so that synchronization or reprocessing does not mix implicit orphan deletion with explicit repository deletion for the same links.

#### Scenario: Follow-up sync replaces services on an existing event
- **GIVEN** a calendar event already persisted with one or more service links
- **WHEN** synchronization recalculates the canonical service set for that same persisted event
- **THEN** the previous links SHALL be removed through one canonical replacement path
- **AND** the new canonical links SHALL be persisted without duplicate-key failure
- **AND** the operation SHALL complete without stale delete failure for already removed rows

#### Scenario: Reprocessing replaces links after prior enrichment
- **GIVEN** a calendar event that was previously enriched or partially associated
- **WHEN** a later reprocessing pass needs to replace the event's canonical service set
- **THEN** the persisted links SHALL converge to exactly one canonical set for the event
- **AND** repeated delete responsibilities for the same link rows SHALL NOT be scheduled

### Requirement: Enriched appointments SHALL remain stable on later synchronization
The system SHALL preserve the canonical persisted service-link set of an enriched appointment across later synchronization runs, including when the event re-enters the replace path.

#### Scenario: Enriched multi-service appointment is synchronized again
- **GIVEN** an appointment previously enriched to include late-created services
- **WHEN** the user triggers synchronization again without a meaningful title change in Google Calendar
- **THEN** the event SHALL remain associated with exactly one link per matched service
- **AND** synchronization SHALL complete successfully
- **AND** the event total and service breakdown SHALL remain consistent with the canonical link set
