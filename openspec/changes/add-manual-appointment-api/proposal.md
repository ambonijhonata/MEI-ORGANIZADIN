## Why

The API currently supports synced calendar events, catalog clients, services, and appointment payments, but it still has no backend flow to create a new appointment directly from the application. The Android app already has a full appointment form and is blocked on a server-side contract that can persist manual appointments now without closing the door on later publication to Google Calendar.

## What Changes

- Add API support to create manual appointments directly in the backend through a new authenticated endpoint.
- Evolve the `calendar_events` model so a calendar event can exist without an immediate Google Calendar link.
- Introduce explicit event origin tracking so the system can distinguish appointments created manually from appointments imported from Google.
- Validate manual appointment creation with the same business rules enforced by the app:
  - client is required
  - at least one service is required
  - end time must be greater than start time
  - client and services must belong to the authenticated user
- Persist manual appointments in the same aggregate used by the calendar home screen, including client link, service links, value snapshots, and generated title.
- Ensure existing Google sync and reconciliation flows do not delete or mutate manual appointments as if they were Google-owned events.

## Capabilities

### New Capabilities
- `manual-appointments`: create and persist manual appointments in the calendar domain with origin metadata, validation, and calendar-list compatibility

### Modified Capabilities

## Impact

- **API**: new authenticated endpoint for manual appointment creation under `/api/calendar/events`
- **Domain model**: `CalendarEvent` gains explicit origin semantics and allows missing `google_event_id` for manual records
- **Database**: new Flyway migration to evolve `calendar_events` for manual appointments
- **Calendar behavior**: existing `/api/calendar/events` listing must continue to surface both manual and Google-backed appointments correctly
- **Sync safety**: Google sync/reconciliation paths must explicitly ignore manual appointments when applying Google-driven deletion and replacement logic
- **Tests**: new service, repository, and controller coverage for creation, validation failures, ownership checks, and coexistence with sync flows
