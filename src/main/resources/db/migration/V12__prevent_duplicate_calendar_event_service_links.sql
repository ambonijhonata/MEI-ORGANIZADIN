-- This migration intentionally fails if residual duplicates still exist.
-- Run the remediation script in docs/sql/duplicate-calendar-event-service-links-remediation.sql first.

CREATE UNIQUE INDEX ux_calendar_event_services_event_service
    ON calendar_event_services (calendar_event_id, service_id)
    WHERE service_id IS NOT NULL;

CREATE UNIQUE INDEX ux_calendar_event_services_snapshot
    ON calendar_event_services (calendar_event_id, service_description_snapshot, service_value_snapshot)
    WHERE service_id IS NULL;
