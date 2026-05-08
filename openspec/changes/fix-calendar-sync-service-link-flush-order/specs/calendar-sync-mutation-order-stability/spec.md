## ADDED Requirements

### Requirement: Mixed sync mutations must persist in deterministic order
The system SHALL persist calendar sync chunks that combine deletions and persisted-event service-link replacement in a deterministic order that removes replaced `calendar_event_services` rows before any conflicting replacement insert is flushed.

#### Scenario: Replace and deletion occur in the same chunk
- **WHEN** a sync chunk contains at least one deleted event and at least one persisted event whose recognized services changed
- **THEN** the system MUST remove the existing `calendar_event_services` rows for the replaced event before any cleanup step in the same chunk can flush replacement inserts

### Requirement: Cleanup operations must not trigger conflicting autoflush during service-link replacement
The system SHALL ensure auxiliary cleanup operations executed during sync do not trigger an autoflush that persists replacement service links while stale rows for the same `(calendar_event_id, service_id)` are still present.

#### Scenario: Payment cleanup coexists with service-link replacement
- **WHEN** sync needs to clean payments for deleted events and also replace service links for another persisted event in the same transaction
- **THEN** the cleanup flow MUST complete without violating the unique constraint on `calendar_event_services (calendar_event_id, service_id)`

### Requirement: Sync endpoint remains reliable for the affected mixed-mutation flow
The system SHALL complete `POST /api/calendar/sync` without surfacing an internal server error when the only conflict risk is the mixed-mutation ordering between deletions and service-link replacement.

#### Scenario: Problematic account re-runs sync after the ordering fix
- **WHEN** the affected account triggers manual or background sync after the ordering fix is deployed
- **THEN** the sync MUST finish without `INTERNAL_SERVER_ERROR` caused by duplicate `calendar_event_services` inserts and the event MUST retain exactly one canonical link per recognized service
