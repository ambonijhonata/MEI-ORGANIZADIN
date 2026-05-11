ALTER TABLE calendar_event_services
    ADD COLUMN occurrence_index INTEGER NOT NULL DEFAULT 0;

DROP INDEX IF EXISTS ux_calendar_event_services_event_service;
DROP INDEX IF EXISTS ux_calendar_event_services_snapshot;

CREATE UNIQUE INDEX ux_calendar_event_services_event_service_occurrence
    ON calendar_event_services (calendar_event_id, service_id, occurrence_index)
    WHERE service_id IS NOT NULL;

CREATE UNIQUE INDEX ux_calendar_event_services_snapshot_occurrence
    ON calendar_event_services (calendar_event_id, service_description_snapshot, service_value_snapshot, occurrence_index)
    WHERE service_id IS NULL;
