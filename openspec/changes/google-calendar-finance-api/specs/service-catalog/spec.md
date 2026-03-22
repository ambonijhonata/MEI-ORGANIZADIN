## ADDED Requirements

### Requirement: Create service
The system SHALL allow an authenticated user to create a service by providing a description and a monetary value. The description SHALL be normalized and stored alongside the original.

#### Scenario: Successful service creation
- **WHEN** an authenticated user submits a valid description and positive value
- **THEN** the system SHALL create the service, normalize the description, and return the created service

#### Scenario: Empty description rejected
- **WHEN** an authenticated user submits an empty or blank description
- **THEN** the system SHALL reject the request with a validation error

#### Scenario: Invalid value rejected
- **WHEN** an authenticated user submits a zero, negative, or null value
- **THEN** the system SHALL reject the request with a validation error

#### Scenario: Duplicate service rejected
- **WHEN** an authenticated user submits a description whose normalized form matches an existing service of the same user
- **THEN** the system SHALL reject the request indicating logical duplication

### Requirement: Normalize service description
The system SHALL normalize service descriptions by: trimming leading/trailing spaces, collapsing multiple spaces into one, converting to lowercase, and removing diacritics/accents.

#### Scenario: Equivalent descriptions normalize to same value
- **WHEN** descriptions "Design de Sobrancelha", "design de sobrancelha", " design   de sobrancelha " are normalized
- **THEN** all SHALL produce the same normalized value

### Requirement: Get single service
The system SHALL allow an authenticated user to retrieve a single service by its ID, provided it belongs to the authenticated user.

#### Scenario: Successful retrieval
- **WHEN** an authenticated user requests a service by ID that belongs to them
- **THEN** the system SHALL return the service details

#### Scenario: Service not found
- **WHEN** an authenticated user requests a service by ID that does not exist or belongs to another user
- **THEN** the system SHALL return HTTP 404

### Requirement: List services for authenticated user
The system SHALL return only services belonging to the authenticated user. By default, results SHALL be ordered by id ascending. The system SHALL support pagination with configurable page size and return pagination metadata.

#### Scenario: User lists their services
- **WHEN** an authenticated user requests their service list without filters
- **THEN** the system SHALL return only services owned by that user, ordered by id ascending, paginated with default size 25

#### Scenario: User cannot see another user's services
- **WHEN** user A requests the service list
- **THEN** the response SHALL NOT contain any services belonging to user B

#### Scenario: Pagination metadata
- **WHEN** an authenticated user requests services with page=0 and size=10
- **THEN** the response SHALL include `totalItems`, `itemsPerPage`, `totalPages`, and `pageIndex`
- **AND** `itemsPerPage` SHALL be 10
- **AND** `pageIndex` SHALL be 0

#### Scenario: Default pagination
- **WHEN** an authenticated user requests services without specifying page or size
- **THEN** the system SHALL use page=0 and size=25

### Requirement: Search services by description
The system SHALL allow filtering the service list by a partial, case-insensitive match on the description field.

#### Scenario: Search with matching term
- **WHEN** an authenticated user searches services with description "sobrancelha"
- **THEN** the system SHALL return only services whose description contains "sobrancelha" (case-insensitive)

#### Scenario: Search with no matches
- **WHEN** an authenticated user searches services with a term that matches no service
- **THEN** the system SHALL return an empty list

#### Scenario: Search without filter
- **WHEN** an authenticated user lists services without a description filter
- **THEN** the system SHALL return all services of the user

### Requirement: Sort services
The system SHALL allow sorting the service list by description, value, or createdAt, in ascending or descending order. Default sort SHALL be id ascending.

#### Scenario: Sort by description ascending
- **WHEN** an authenticated user requests services sorted by description ascending
- **THEN** the system SHALL return services alphabetically ordered by description

#### Scenario: Sort by value descending
- **WHEN** an authenticated user requests services sorted by value descending
- **THEN** the system SHALL return services ordered from highest to lowest value

### Requirement: Update service
The system SHALL allow an authenticated user to update the description and/or value of their own service.

#### Scenario: Successful update
- **WHEN** an authenticated user updates description or value of their own service
- **THEN** the system SHALL persist the changes and update the normalized description if description changed

#### Scenario: Update triggers reprocessing
- **WHEN** an authenticated user updates a service description
- **THEN** the system SHALL trigger asynchronous reprocessing of unidentified events to attempt new matches

#### Scenario: Creation triggers reprocessing
- **WHEN** an authenticated user creates a new service
- **THEN** the system SHALL trigger asynchronous reprocessing of unidentified events to attempt matches with the new service

### Requirement: Delete service without linked events
The system SHALL allow deletion of a service only when it has no linked synchronized events.

#### Scenario: Delete service with no links
- **WHEN** an authenticated user deletes a service that has no synchronized events linked to it
- **THEN** the system SHALL delete the service successfully

#### Scenario: Reject deletion of service with linked events
- **WHEN** an authenticated user attempts to delete a service that has synchronized events linked to it
- **THEN** the system SHALL reject the deletion with an explicit business error

### Requirement: Service belongs to single user
Each service SHALL belong to exactly one user. A service from one user SHALL NEVER be used to identify events of another user.

#### Scenario: Cross-user isolation
- **WHEN** user A creates a service and user B has events
- **THEN** user A's service SHALL NOT be matched against user B's events during synchronization
