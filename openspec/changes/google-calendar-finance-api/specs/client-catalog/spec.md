## ADDED Requirements

### Requirement: Create client
The system SHALL allow an authenticated user to create a client by providing a name (required), and optionally cpf, date of birth, email and phone number.

#### Scenario: Successful client creation with name only
- **WHEN** an authenticated user submits a valid name
- **THEN** the system SHALL create the client associated with the authenticated user

#### Scenario: Successful client creation with all fields
- **WHEN** an authenticated user submits name, cpf, date of birth, email and phone
- **THEN** the system SHALL create the client with all provided fields

#### Scenario: Empty name rejected
- **WHEN** an authenticated user submits an empty or blank name
- **THEN** the system SHALL reject the request with a validation error

### Requirement: Get single client
The system SHALL allow an authenticated user to retrieve a single client by its ID, provided it belongs to the authenticated user.

#### Scenario: Successful retrieval
- **WHEN** an authenticated user requests a client by ID that belongs to them
- **THEN** the system SHALL return the client details

#### Scenario: Client not found
- **WHEN** an authenticated user requests a client by ID that does not exist or belongs to another user
- **THEN** the system SHALL return HTTP 404

### Requirement: Check client appointment link
The system SHALL provide an endpoint to check whether a client (identified by name) has any linked calendar appointments.

#### Scenario: Client has linked appointments
- **WHEN** an authenticated user checks a client name that has linked calendar events
- **THEN** the system SHALL return true

#### Scenario: Client has no linked appointments
- **WHEN** an authenticated user checks a client name that has no linked calendar events
- **THEN** the system SHALL return false

#### Scenario: Client not found by name
- **WHEN** an authenticated user checks a client name that does not exist
- **THEN** the system SHALL return false

### Requirement: List clients for authenticated user
The system SHALL return only clients belonging to the authenticated user. By default, results SHALL be ordered by id ascending.

#### Scenario: User lists their clients
- **WHEN** an authenticated user requests their client list without filters
- **THEN** the system SHALL return only clients owned by that user, ordered by id ascending

### Requirement: Search clients by name
The system SHALL allow filtering the client list by a partial, case-insensitive match on the name field.

#### Scenario: Search with matching term
- **WHEN** an authenticated user searches clients with name "maria"
- **THEN** the system SHALL return only clients whose name contains "maria" (case-insensitive)

#### Scenario: Search with no matches
- **WHEN** an authenticated user searches clients with a term that matches no client
- **THEN** the system SHALL return an empty list

#### Scenario: Search without filter
- **WHEN** an authenticated user lists clients without a name filter
- **THEN** the system SHALL return all clients of the user

### Requirement: Sort clients
The system SHALL allow sorting the client list by name, cpf, dateOfBirth, email, phone, or createdAt, in ascending or descending order. Default sort SHALL be id ascending.

#### Scenario: Sort by name ascending
- **WHEN** an authenticated user requests clients sorted by name ascending
- **THEN** the system SHALL return clients alphabetically ordered by name

#### Scenario: Sort by createdAt descending
- **WHEN** an authenticated user requests clients sorted by createdAt descending
- **THEN** the system SHALL return clients ordered from newest to oldest

### Requirement: Update client
The system SHALL allow an authenticated user to update any field of their own client.

#### Scenario: Successful update
- **WHEN** an authenticated user updates fields of their own client
- **THEN** the system SHALL persist the changes

### Requirement: Delete client
The system SHALL allow an authenticated user to delete their own client only when no appointments are linked to it.

#### Scenario: Delete client with no linked appointments
- **WHEN** an authenticated user deletes a client that has no linked appointments
- **THEN** the system SHALL delete the client successfully

#### Scenario: Reject deletion of client with linked appointments
- **WHEN** an authenticated user attempts to delete a client that has linked appointments
- **THEN** the system SHALL reject the deletion with a business error

### Requirement: Auto-create client during sync
The system SHALL automatically create a client when a calendar event is synchronized with a client name that does not match any existing client of the user. The match SHALL be case-insensitive and trimmed.

#### Scenario: New client from sync
- **WHEN** a calendar event title contains a client name not yet registered
- **THEN** the system SHALL create the client with the parsed name

#### Scenario: Existing client from sync
- **WHEN** a calendar event title contains a client name that matches an existing client
- **THEN** the system SHALL link the appointment to the existing client

### Requirement: Client belongs to single user
Each client SHALL belong to exactly one user. Clients are isolated per user.

#### Scenario: Cross-user isolation
- **WHEN** user A has a client named "Maria"
- **THEN** user B SHALL NOT see or reference user A's client
