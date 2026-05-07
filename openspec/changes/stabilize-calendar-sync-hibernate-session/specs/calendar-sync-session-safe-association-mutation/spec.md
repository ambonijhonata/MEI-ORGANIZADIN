## ADDED Requirements

### Requirement: Existing calendar events MUST be mutated only while managed by an active persistence context
The system SHALL perform service-association mutations for existing `CalendarEvent` rows only when the target entity is attached to an active JPA persistence context and its required association state has been loaded for that mutation.

#### Scenario: Existing event updates services without lazy initialization failure
- **GIVEN** an existing calendar event persisted with lazy `serviceLinks`
- **WHEN** `POST /api/calendar/sync` processes a Google event that changes the matched services for that existing event
- **THEN** the implementation MUST mutate the association using a managed entity inside an active transaction
- **AND** the request MUST NOT fail with `LazyInitializationException`

#### Scenario: Existing event clears services without detached collection access
- **GIVEN** an existing calendar event persisted with one or more linked services
- **WHEN** sync determines that no services should remain associated to that event
- **THEN** `clearServiceAssociation` MUST execute without touching an uninitialized detached collection
- **AND** orphan removal semantics MUST still be preserved

### Requirement: Sync processing MUST NOT rely on Open Session In View
The system SHALL remain compatible with `spring.jpa.open-in-view=false` while processing calendar sync updates.

#### Scenario: Sync remains safe with open-in-view disabled
- **GIVEN** application runtime has `spring.jpa.open-in-view=false`
- **WHEN** sync updates existing events and their service associations
- **THEN** the implementation MUST use explicit transactional persistence-context management rather than web-request session extension
