## Context

The backend already persists appointments in `calendar_events`, but that aggregate is currently modeled around Google Calendar ingestion. `google_event_id` is mandatory in both the entity and the database schema, sync logic uses Google-backed queries as the primary source of truth, and the only write flow on appointments today is payment composition.

At the same time, the Android app now has a complete appointment form with client, date, time-range, and multi-service selection. The app can already load clients and services from the API, but it still performs a local-only save because the backend cannot create manual appointments yet.

This change needs to bridge that gap without painting the system into a corner. A later phase is expected to publish manually created appointments to Google Calendar, so the new model must distinguish event origin from Google linkage instead of assuming they are the same thing.

## Goals / Non-Goals

**Goals:**
- Allow authenticated users to create manual appointments directly in the API.
- Reuse `CalendarEvent` as the persisted aggregate so new appointments appear in existing calendar listing flows.
- Add explicit event-origin semantics that support both:
  - manually created appointments with no Google event yet
  - manually created appointments that may later be linked to Google
  - appointments imported from Google sync
- Preserve existing client and service ownership boundaries and service snapshot behavior.
- Protect Google sync and reconciliation logic from treating manual appointments as Google-owned events.

**Non-Goals:**
- Creating or updating the Google Calendar event during the same request.
- Editing or deleting manual appointments.
- Changing payment composition rules.
- Redesigning existing calendar listing contracts beyond what is required for coexistence.

## Decisions

### 1. Add explicit origin metadata to `CalendarEvent`

**Decision:** Introduce a required `source` enum on `CalendarEvent` with values `GOOGLE` and `MANUAL`, and allow `google_event_id` to be nullable.

**Why:** The system needs to represent three valid business states:
- manual appointment not yet linked to Google
- manual appointment later linked to Google
- appointment imported from Google

`source` captures where the event was born; `google_event_id` captures whether a Google event is linked. Keeping both concepts separate supports the later “publish to Google” phase cleanly.

**Alternatives considered:**
- Keep `google_event_id` mandatory and generate synthetic IDs for manual appointments: rejected because it blurs internal identity with an external provider identifier and makes future sync behavior harder to reason about.
- Use only nullable `google_event_id` without `source`: rejected because a linked manual event and a Google-imported event would become indistinguishable in downstream logic.

### 2. Manual appointments will be created through `POST /api/calendar/events`

**Decision:** Add a new authenticated endpoint under the existing calendar controller boundary:

`POST /api/calendar/events`

with a request contract that includes:
- `clientId`
- `appointmentDate`
- `startTime`
- `endTime`
- `serviceIds`

**Why:** The app already treats appointments as part of the calendar domain, and the existing list endpoint also lives under `/api/calendar/events`. Keeping creation under the same resource family gives the API a coherent shape.

**Alternatives considered:**
- Create a separate `/api/appointments` controller: rejected for now because the persisted aggregate and UI read path already use calendar events, so a parallel resource would add indirection without solving a current problem.

### 3. Manual appointment creation will use a dedicated service, not `CalendarSyncService`

**Decision:** Implement manual creation in a new application service dedicated to manual appointment writes.

**Why:** `CalendarSyncService` is optimized for ingesting and reconciling Google events in bulk. Manual creation has a different responsibility set: ownership validation, request validation, title generation, and aggregate assembly from first-party inputs. Keeping the flows separate reduces risk and keeps sync behavior understandable.

**Alternatives considered:**
- Reuse `CalendarSyncService` or force manual requests through sync-oriented mutation helpers: rejected because it couples a synchronous user write path to Google-specific reconciliation logic.

### 4. Manual appointments will be persisted as identified events with service snapshots

**Decision:** A manual appointment SHALL persist:
- `source = MANUAL`
- `google_event_id = null`
- `identified = true`
- selected client link
- selected service links in `calendar_event_services`
- `service_description_snapshot` and `service_value_snapshot`
- generated `title`

The aggregate should reuse the existing snapshot pattern already used for synced events.

**Why:** The user is explicitly selecting client and services, so there is no unresolved matching state. Persisting snapshots preserves historical financial meaning even if the service catalog changes later.

**Alternatives considered:**
- Mark manual events as unidentified until later enrichment: rejected because it does not reflect the explicit user input and would complicate reporting and UI display.

### 5. Generated title will be based on client + selected services

**Decision:** The backend will generate the appointment title from the selected client and services, following the same information shape the calendar UI already expects.

Recommended composition:
- `<client name> - <service 1> + <service 2> + ...`

**Why:** The home calendar screen displays `title` directly today. Manual appointments need a meaningful title immediately, without waiting for later Google publication.

**Alternatives considered:**
- Persist a blank or generic title and let the app compose display text: rejected because it would create divergent display logic and weaken the API as the canonical source of event data.

### 6. Sync logic will remain scoped to Google-backed events only

**Decision:** Existing sync and deletion/replacement logic will explicitly continue to operate only on Google-backed events. Manual events must never be considered reconciliation candidates unless a later feature explicitly opts them in after Google linkage.

**Why:** This is the safety boundary that prevents a later incremental sync from deleting a manual appointment that happens to share a date range or title shape with Google data.

**Alternatives considered:**
- Let full sync load all events and infer which ones are safe to reconcile: rejected because the failure mode is destructive and unnecessary when origin is known.

## Risks / Trade-offs

- **Schema evolution on a central table** -> Mitigation: add a dedicated Flyway migration with explicit nullable/enum changes and verify Hibernate `ddl-auto: validate` against the updated model.
- **Existing sync code may implicitly assume `google_event_id` is always present** -> Mitigation: review Google-backed queries, deletion paths, and any code branching on `google_event_id` so null handling becomes explicit.
- **Manual title generation may become a contract the UI depends on** -> Mitigation: document the generated title shape and keep it server-side as a stable convention.
- **Future publication to Google may need additional status tracking** -> Mitigation: keep this change limited to `source + google_event_id`; defer operational sync-state fields until the Google-create flow actually exists.

## Migration Plan

1. Add Flyway migration to evolve `calendar_events`:
   - allow `google_event_id` to be null
   - add `source`
   - backfill existing rows to `source = GOOGLE`
   - preserve uniqueness semantics for Google-linked rows
2. Update JPA model and repository queries to reflect the new event state model.
3. Introduce the manual appointment creation service and controller endpoint.
4. Verify calendar listing still returns both manual and Google-backed events for the selected day.
5. Verify Google sync and reprocessing continue to operate only on Google-backed events.

Rollback approach:
- If deployment fails before manual appointment traffic starts, revert the application code and restore the previous schema through a compensating migration.
- If deployment fails after manual appointments are created, rollback must preserve manual rows; do not restore the old non-null `google_event_id` constraint without first migrating or exporting those records.

## Open Questions

- Should the create endpoint return the full created event payload, or a creation-focused response with the new local ID plus summary fields?
- Should manual appointment creation already detect scheduling conflicts with another local or Google-backed event for the same user, or is overlap detection a later feature?
- When the later Google-create flow is introduced, should a manual event keep `source = MANUAL` forever, or will a second status field be introduced to represent publication state?
