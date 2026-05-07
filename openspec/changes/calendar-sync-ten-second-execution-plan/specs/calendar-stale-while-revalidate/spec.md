## MODIFIED Requirements

### Requirement: Background Revalidation Using Existing Sync Endpoint
After immediate rendering, the system SHALL revalidate calendar data in the background using the existing sync endpoint contract, with strict non-blocking behavior even under heavy sync duration.

#### Scenario: Background sync runs after local render
- **WHEN** agenda data for the selected date is rendered
- **THEN** the app MUST trigger `POST /api/calendar/sync` asynchronously and MUST NOT block current agenda visibility

#### Scenario: API contract remains unchanged during revalidation
- **WHEN** stale-while-revalidate is enabled
- **THEN** the app MUST continue consuming existing `sync`, `events`, and `status` endpoints without HTTP or payload contract changes

#### Scenario: Heavy sync duration does not degrade navigation responsiveness
- **WHEN** a background sync takes significantly longer than expected
- **THEN** the app MUST keep date navigation and agenda rendering non-blocking while sync completion remains asynchronous
