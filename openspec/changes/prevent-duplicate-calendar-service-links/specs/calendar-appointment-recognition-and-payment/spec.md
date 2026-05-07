## ADDED Requirements

### Requirement: Service association persistence SHALL be idempotent per event
The system SHALL persist at most one canonical service link per logical service association on a calendar event, even when the same event is processed repeatedly by synchronization or unidentified-event reprocessing.

#### Scenario: Repeated sync of a single-service event
- **WHEN** the same identified event for `sobrancelha` is synchronized more than once without a real service change
- **THEN** the event SHALL keep a single persisted service link for `sobrancelha`
- **AND** the event SHALL keep the same total service snapshot for reporting

#### Scenario: Repeated reprocessing after service catalog creation
- **WHEN** an unidentified event becomes identifiable after service creation and the reprocessing flow is triggered multiple times
- **THEN** the event SHALL persist only one link for each matched service
- **AND** the event SHALL NOT accumulate duplicate links from repeated reprocessing

### Requirement: Concurrent service-association flows SHALL converge to one canonical link set
The system SHALL guarantee that overlapping per-user sync and reprocessing executions converge to a single canonical set of service links for each event instead of duplicating existing associations.

#### Scenario: Sync and reprocessing overlap on the same event
- **WHEN** background sync and unidentified-event reprocessing attempt to associate services to the same event concurrently
- **THEN** the final persisted event state SHALL contain only one canonical link set for the matched services
- **AND** duplicate rows for the same event-service association SHALL NOT be committed

#### Scenario: Multi-service event remains stable after later sync
- **WHEN** an event already associated with `sobrancelha` and `buco` is synchronized again after reprocessing completed
- **THEN** the event SHALL still contain exactly one link for `sobrancelha`
- **AND** the event SHALL still contain exactly one link for `buco`

### Requirement: Cash-flow service totals SHALL use deduplicated event-service associations
The system SHALL ensure that service totals derived from event-service associations reflect the canonical association set for each event and SHALL NOT double count duplicated persistence attempts.

#### Scenario: Single-service event appears in cash flow after repeated processing
- **WHEN** a single-service event has been processed by both initial sync and later catalog-driven reprocessing
- **THEN** the cash-flow breakdown SHALL count that service once for the event
- **AND** the daily total SHALL remain equal to the event service snapshot

#### Scenario: Mixed-service event appears in cash flow after repeated processing
- **WHEN** a multi-service event has been processed repeatedly without real service changes
- **THEN** each matched service SHALL contribute once to the cash-flow breakdown
- **AND** the sum of service contributions SHALL remain equal to the event service snapshot total
