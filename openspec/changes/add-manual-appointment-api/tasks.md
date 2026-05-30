## 1. Schema and Domain Model

- [x] 1.1 Add a Flyway migration that makes `calendar_events.google_event_id` nullable, introduces the required event origin column, and backfills existing records as Google-origin.
- [x] 1.2 Update `CalendarEvent` and any related enums/builders so manual events can be persisted with `source = MANUAL` and no Google event id.
- [x] 1.3 Review repository queries and uniqueness assumptions so Google-backed lookups remain explicit and safe after the schema change.

## 2. Manual Appointment Creation Flow

- [x] 2.1 Add the request/response contract and authenticated `POST /api/calendar/events` endpoint in the calendar controller.
- [x] 2.2 Implement a dedicated manual appointment service that validates ownership, required fields, time-range ordering, title generation, and aggregate assembly.
- [x] 2.3 Persist client linkage, service links, and service snapshots so created manual appointments are immediately compatible with calendar listing and payment flows.

## 3. Sync and Listing Safety

- [x] 3.1 Ensure manual appointments created through the new endpoint appear in the existing `/api/calendar/events` listing for the selected day.
- [x] 3.2 Adjust Google sync and reconciliation paths so manual-only appointments are never selected for Google-driven deletion or replacement logic.

## 4. Verification

- [x] 4.1 Add or update tests for successful manual appointment creation, validation failures, and cross-user ownership rejection.
- [x] 4.2 Add or update tests covering schema/backfill assumptions and coexistence between manual appointments and Google sync behavior.
- [x] 4.3 Run the relevant API verification tasks for the manual appointment creation flow.
