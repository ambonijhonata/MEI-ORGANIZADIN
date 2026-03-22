## ADDED Requirements

### Requirement: Bulk delete services with partial processing
The system SHALL provide `POST /api/services/delete` for authenticated users, receiving a JSON array of service IDs. The system SHALL process each owned service independently instead of failing the entire request when some services cannot be deleted.

#### Scenario: Mixed bulk deletion result
- **WHEN** an authenticated user submits `[10, 11, 12]` and services `10` and `12` have no links while `11` has linked calendar events
- **THEN** the system SHALL delete services `10` and `12`
- **AND** the system SHALL keep service `11`
- **AND** the response SHALL be `{"deleted": 2, "hasLink": 1}`

#### Scenario: All services linked
- **WHEN** an authenticated user submits IDs and all owned services in the request have linked calendar events
- **THEN** the system SHALL delete no services
- **AND** the response SHALL contain `deleted = 0`
- **AND** the response SHALL contain `hasLink` equal to the number of linked owned services in the request

#### Scenario: Empty list
- **WHEN** an authenticated user submits an empty array `[]`
- **THEN** the system SHALL perform no deletions
- **AND** the response SHALL be `{"deleted": 0, "hasLink": 0}`

### Requirement: Link-aware protection in bulk delete
For each owned service ID in the bulk request, the system SHALL verify whether the service is linked to synchronized calendar events before deletion.

#### Scenario: Linked service is ignored
- **WHEN** an owned service ID in the request has at least one linked synchronized event
- **THEN** the system SHALL NOT delete that service
- **AND** the system SHALL increment `hasLink` by 1

#### Scenario: Unlinked service is deleted
- **WHEN** an owned service ID in the request has no linked synchronized events
- **THEN** the system SHALL delete that service
- **AND** the system SHALL increment `deleted` by 1

### Requirement: Ignore unknown or foreign IDs in bulk delete
The system SHALL ignore request IDs that do not resolve to services owned by the authenticated user.

#### Scenario: Unknown ID ignored
- **WHEN** an authenticated user submits an ID that does not exist
- **THEN** the system SHALL ignore that ID
- **AND** the ID SHALL NOT increment `deleted` or `hasLink`

#### Scenario: Foreign service ID ignored
- **WHEN** an authenticated user submits an ID belonging to another user
- **THEN** the system SHALL ignore that ID
- **AND** the ID SHALL NOT increment `deleted` or `hasLink`
