## ADDED Requirements

### Requirement: Full sync on first synchronization
The system SHALL perform a full sync of all events from the user's primary Google Calendar when no prior sync state exists for the user.

#### Scenario: First sync for new user
- **WHEN** a user has no prior sync state
- **THEN** the system SHALL fetch all events from the primary calendar (no date filter), paginate through all pages, persist each event, attempt service identification for each, and store the sync state for future incremental syncs

#### Scenario: Full sync records timestamp
- **WHEN** a full sync completes successfully
- **THEN** the system SHALL record the timestamp of the last completed synchronization

### Requirement: Incremental sync for subsequent synchronizations
The system SHALL perform incremental sync using the stored sync state to fetch only events created, modified, or removed since the last synchronization.

#### Scenario: Incremental sync fetches only changes
- **WHEN** a user has existing sync state and a sync is triggered
- **THEN** the system SHALL use the stored sync state to request only changed events from Google Calendar

#### Scenario: Incremental sync applies changes
- **WHEN** incremental sync returns new, modified, and deleted events
- **THEN** the system SHALL insert new events, update modified events (reapplying service identification), and delete locally events that were removed in Google

#### Scenario: Incremental sync updates state
- **WHEN** incremental sync completes
- **THEN** the system SHALL update the stored sync state to the new value returned by Google

### Requirement: Full resync on expired sync state
The system SHALL perform a full resync when Google indicates the sync state is invalid or expired (HTTP 410 Gone).

#### Scenario: Sync state expired
- **WHEN** the system attempts incremental sync and Google returns 410 Gone
- **THEN** the system SHALL discard the current sync state, execute a new full sync, and store the new sync state

### Requirement: Operate on primary calendar only
The system SHALL synchronize events exclusively from the user's primary Google Calendar. It SHALL NOT list, sync, or allow selection of secondary or shared calendars.

#### Scenario: Only primary calendar events synced
- **WHEN** synchronization is performed
- **THEN** only events from the primary calendar (calendarId="primary") SHALL be fetched

### Requirement: Parse event title for client and services
The system SHALL parse the event title using the format `{client name} - {service1} + {service2} + {serviceN}`. The part before the first ` - ` is the client name. The part after is a `+`-separated list of service names.

#### Scenario: Title with client and multiple services
- **WHEN** an event has title "fabiane honorato - sobrancelha + buço + henna + rosto"
- **THEN** the system SHALL parse client name as "fabiane honorato" and service names as ["sobrancelha", "buço", "henna", "rosto"]

#### Scenario: Title with client and single service
- **WHEN** an event has title "maria silva - corte"
- **THEN** the system SHALL parse client name as "maria silva" and service names as ["corte"]

#### Scenario: Title without separator
- **WHEN** an event has title without ` - ` separator
- **THEN** the system SHALL treat the entire title as a single service name with no client, maintaining backward compatibility

### Requirement: Resolve client from event title
For each synchronized event with a parsed client name, the system SHALL find or create a client for the authenticated user.

#### Scenario: Existing client matched
- **WHEN** the parsed client name matches an existing client of the user (case-insensitive, trimmed)
- **THEN** the system SHALL link the appointment to the existing client

#### Scenario: New client auto-created
- **WHEN** the parsed client name does not match any existing client of the user
- **THEN** the system SHALL create a new client with the parsed name and link the appointment to it

### Requirement: Identify multiple services from event title
For each synchronized event, the system SHALL attempt to match each parsed service name against the user's service catalog using normalized comparison. Unmatched services SHALL be discarded silently.

#### Scenario: All services matched
- **WHEN** all parsed service names match services in the user's catalog
- **THEN** the system SHALL link the appointment to all matched services with their respective value snapshots

#### Scenario: Partial services matched
- **WHEN** some parsed service names match and others do not
- **THEN** the system SHALL link the appointment only to the matched services and discard the unmatched ones

#### Scenario: No services matched
- **WHEN** none of the parsed service names match any service in the user's catalog
- **THEN** the system SHALL save the appointment with no linked services and mark it as not identified

### Requirement: Persist historical value snapshot per service
When an appointment is associated with services, the system SHALL record each service link with the service description and value at the time of association.

#### Scenario: Value snapshot is independent of future price changes
- **WHEN** a service's value is changed after events were already associated with it
- **THEN** the previously associated events SHALL retain the original snapshot values

### Requirement: Delete local events removed from Google
When Google reports an event as deleted/cancelled, the system SHALL delete the corresponding synchronized event from the local database. Deleted events SHALL NOT remain as "cancelled" records.

#### Scenario: Event removed in Google
- **WHEN** incremental sync reports an event as deleted in Google
- **THEN** the system SHALL delete the event from the local database

#### Scenario: Deleted events excluded from reports
- **WHEN** an event is deleted from the local database
- **THEN** it SHALL NOT appear in any subsequent event listings or financial reports

### Requirement: Reprocess unidentified events after service changes
When a service is created or its description is updated, the system SHALL asynchronously attempt to match previously unidentified events of the same user against the new/updated service.

#### Scenario: New service matches existing unidentified event
- **WHEN** a user creates a new service whose normalized description matches an existing unidentified event's normalized title
- **THEN** the system SHALL associate the event with the service, record the value snapshot, and mark it as identified

### Requirement: Manual sync trigger
The system SHALL provide a capability for authenticated users to manually trigger a synchronization of their calendar events.

#### Scenario: User triggers manual sync
- **WHEN** an authenticated user requests a manual sync
- **THEN** the system SHALL execute an incremental sync (or full resync if state is invalid) for that user

### Requirement: Read synchronized events
The system SHALL provide paginated, authenticated read access to synchronized events for the authenticated user, including service identification status and value snapshot when available. The endpoint SHALL support optional date filters (eventStart, eventEnd) to restrict results to a specific period.

#### Scenario: List events with pagination
- **WHEN** an authenticated user requests their synchronized events
- **THEN** the system SHALL return a paginated list of events belonging only to that user, including service identification info

#### Scenario: Filter events by date range
- **WHEN** an authenticated user requests events with eventStart and/or eventEnd query parameters
- **THEN** the system SHALL return only events whose eventStart falls within the specified range (inclusive start, exclusive end)

#### Scenario: Filter with only eventStart
- **WHEN** an authenticated user provides only eventStart
- **THEN** the system SHALL return events with eventStart on or after the given date, with no upper bound

#### Scenario: Filter with only eventEnd
- **WHEN** an authenticated user provides only eventEnd
- **THEN** the system SHALL return events with eventStart before the given date (exclusive), with no lower bound

#### Scenario: No date filters provided
- **WHEN** an authenticated user requests events without eventStart or eventEnd
- **THEN** the system SHALL return all events for that user (no date filtering)

#### Scenario: User isolation on event reads
- **WHEN** user A requests events
- **THEN** the response SHALL NOT contain events belonging to user B

### Requirement: Use OAuth credentials for sync
The system SHALL use the stored OAuth credentials (access_token/refresh_token) for Google Calendar API calls. The idToken SHALL NOT be used as a Google Calendar API credential.

#### Scenario: Sync uses persisted OAuth tokens
- **WHEN** a synchronization is performed
- **THEN** the system SHALL use the user's stored access_token (refreshing via refresh_token if needed), not the idToken
