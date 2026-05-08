## ADDED Requirements

### Requirement: Late-created services SHALL enrich synchronized appointments
When a service is created or its description is updated, the system SHALL asynchronously re-evaluate previously synchronized events of the same user using the saved event title and the current service catalog. The enrichment flow SHALL add newly recognized services even when the event is already identified, and SHALL preserve historical snapshots for associations that were already valid.

#### Scenario: Partially identified appointment gains missing services after catalog creation
- **WHEN** an existing synchronized appointment title resolves service tokens `sobrancelha`, `buco`, and `tintura`
- **AND** only `sobrancelha` was available during the original synchronization
- **AND** `buco` and `tintura` are later created in the same user's catalog
- **THEN** the enrichment flow SHALL add associations for `buco` and `tintura` to the existing appointment
- **AND** the previously associated `sobrancelha` snapshot SHALL remain unchanged

#### Scenario: Previously unidentified appointment still becomes identified
- **WHEN** a synchronized appointment previously had no matched services
- **AND** a later service creation or description update makes one or more title tokens match the user's catalog
- **THEN** the enrichment flow SHALL associate the newly matched services to the existing appointment
- **AND** the appointment SHALL become identified

#### Scenario: Later synchronization keeps enriched appointment stable
- **WHEN** an appointment was already enriched with late-created services
- **AND** a later synchronization processes the same Google event without a real title change
- **THEN** the appointment SHALL retain exactly one canonical association for each matched service
- **AND** the event total snapshot SHALL remain equal to the enriched service set
