## ADDED Requirements

### Requirement: Sync result counters MUST represent persisted mutations only
The system SHALL compute `created`, `updated`, and `deleted` based only on mutations effectively applied to local persistence during the sync execution.

#### Scenario: Existing event processed without changes does not increase updated
- **WHEN** an existing Google-backed event is evaluated and no mutable field/association changes are detected
- **THEN** the system MUST NOT increment `updated`

#### Scenario: Existing event with effective mutation increases updated
- **WHEN** an existing event has at least one effective mutable change and is persisted
- **THEN** the system MUST increment `updated` exactly once for that event

#### Scenario: New event increases created
- **WHEN** an incoming event has no corresponding local event and is persisted
- **THEN** the system MUST increment `created` exactly once for that event

#### Scenario: Deleted event increases deleted
- **WHEN** a local Google-backed event is removed due to cancelled/reconciled state in sync
- **THEN** the system MUST increment `deleted` exactly once for that event

### Requirement: Sync success payload MUST keep current shape while using corrected semantics
The system SHALL keep the success payload keys unchanged and SHALL return corrected counter semantics.

#### Scenario: Success response contract remains stable
- **WHEN** sync finishes successfully
- **THEN** the API MUST return HTTP `200` with `created`, `updated`, and `deleted` fields
