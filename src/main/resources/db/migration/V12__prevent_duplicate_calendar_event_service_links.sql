-- This migration self-remediates duplicate calendar_event_services rows before adding unique indexes.
-- The manual remediation script remains available in docs/sql/duplicate-calendar-event-service-links-remediation.sql
-- as an operational fallback for diagnostics or controlled cleanup.

WITH duplicate_service_rows AS (
    SELECT id
    FROM (
        SELECT
            id,
            ROW_NUMBER() OVER (
                PARTITION BY calendar_event_id, service_id
                ORDER BY id
            ) AS rn
        FROM calendar_event_services
        WHERE service_id IS NOT NULL
    ) ranked
    WHERE rn > 1
),
duplicate_snapshot_rows AS (
    SELECT id
    FROM (
        SELECT
            id,
            ROW_NUMBER() OVER (
                PARTITION BY calendar_event_id, service_description_snapshot, service_value_snapshot
                ORDER BY id
            ) AS rn
        FROM calendar_event_services
        WHERE service_id IS NULL
    ) ranked
    WHERE rn > 1
),
rows_to_delete AS (
    SELECT id FROM duplicate_service_rows
    UNION
    SELECT id FROM duplicate_snapshot_rows
)
DELETE FROM calendar_event_services
WHERE id IN (SELECT id FROM rows_to_delete);

CREATE UNIQUE INDEX ux_calendar_event_services_event_service
    ON calendar_event_services (calendar_event_id, service_id)
    WHERE service_id IS NOT NULL;

CREATE UNIQUE INDEX ux_calendar_event_services_snapshot
    ON calendar_event_services (calendar_event_id, service_description_snapshot, service_value_snapshot)
    WHERE service_id IS NULL;
