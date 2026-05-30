## ADDED Requirements

### Requirement: API can create a manual appointment
The system SHALL allow an authenticated user to create a manual appointment through `POST /api/calendar/events` by informing a client, an appointment date, a start time, an end time, and one or more services owned by that same user.

#### Scenario: Successful manual appointment creation
- **WHEN** an authenticated user submits a valid manual appointment request with an owned client and one or more owned services
- **THEN** the API returns a successful response for the created appointment and persists it in the calendar domain

#### Scenario: Missing required manual appointment data
- **WHEN** an authenticated user submits a manual appointment request without a client, without services, or without a complete time range
- **THEN** the API MUST reject the request with validation or business-rule errors that identify the invalid input

#### Scenario: End time is not after start time
- **WHEN** an authenticated user submits a manual appointment request whose end time is less than or equal to the start time
- **THEN** the API MUST reject the request with a business-rule error indicating that end time must be greater than start time

#### Scenario: Cross-user ownership is rejected
- **WHEN** an authenticated user submits a manual appointment request referencing a client or service that belongs to another user
- **THEN** the API MUST reject the request and MUST NOT persist the appointment

### Requirement: Manual appointments use explicit origin semantics
The system SHALL persist manual appointments with explicit origin metadata that distinguishes them from appointments imported from Google Calendar.

#### Scenario: New manual appointment is stored without Google linkage
- **WHEN** the API creates a manual appointment
- **THEN** the persisted event MUST have `source = MANUAL` and `google_event_id = null`

#### Scenario: Existing Google-backed events are preserved as Google-origin events
- **WHEN** the schema migration introducing manual appointment support is applied to existing calendar events
- **THEN** previously persisted Google-backed events MUST retain a valid Google linkage and MUST be marked with `source = GOOGLE`

#### Scenario: Invalid Google-origin state is disallowed
- **WHEN** an event is treated as Google-origin by backend logic
- **THEN** that event MUST have a non-null Google event identifier

### Requirement: Manual appointments reuse the calendar aggregate for listing and finance
The system SHALL persist manual appointments in the same aggregate used by calendar listing and payment flows, including client linkage, service snapshots, and an immediately usable title.

#### Scenario: Manual appointment persists service snapshots
- **WHEN** the API creates a manual appointment with one or more services
- **THEN** it MUST persist service link rows and snapshot the selected service descriptions and values onto the appointment aggregate

#### Scenario: Manual appointment is created as an identified event
- **WHEN** the API creates a manual appointment from explicit user-selected client and services
- **THEN** the event MUST be stored as identified and ready for downstream calendar and payment flows

#### Scenario: Manual appointment appears in existing event listing
- **WHEN** the authenticated user later requests `/api/calendar/events` for the appointment day
- **THEN** the newly created manual appointment MUST be included in the response alongside any Google-backed events for that day

### Requirement: Google sync must not reconcile manual-only appointments
The system SHALL keep Google synchronization and reconciliation scoped to Google-backed events and MUST NOT delete or mutate manual-only appointments as if they originated from Google.

#### Scenario: Full sync ignores manual-only appointments for reconciliation
- **WHEN** a Google full sync runs after manual appointments exist with `source = MANUAL` and `google_event_id = null`
- **THEN** those manual appointments MUST NOT be selected as deletion or replacement candidates by sync reconciliation

#### Scenario: Incremental sync ignores manual-only appointments for reconciliation
- **WHEN** an incremental Google sync runs after manual appointments exist with `source = MANUAL` and `google_event_id = null`
- **THEN** those manual appointments MUST remain persisted unless a non-sync local operation changes them
