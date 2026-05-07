-- Run this cleanup before applying V12__prevent_duplicate_calendar_event_service_links.sql.
-- It preserves the lowest id for each semantic link and removes duplicates.

BEGIN;

WITH duplicate_groups AS (
    SELECT
        calendar_event_id,
        COALESCE(service_id, -1) AS service_key,
        service_description_snapshot,
        service_value_snapshot,
        COUNT(*) AS occurrences
    FROM calendar_event_services
    GROUP BY
        calendar_event_id,
        COALESCE(service_id, -1),
        service_description_snapshot,
        service_value_snapshot
    HAVING COUNT(*) > 1
),
ranked AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY
                calendar_event_id,
                COALESCE(service_id, -1),
                service_description_snapshot,
                service_value_snapshot
            ORDER BY id
        ) AS rn
    FROM calendar_event_services
)
DELETE FROM calendar_event_services ces
USING ranked r
WHERE ces.id = r.id
  AND r.rn > 1;

COMMIT;

-- Diagnostic query: run before cleanup to measure the current scope and after cleanup to verify it is empty.
SELECT
    calendar_event_id,
    COALESCE(service_id, -1) AS service_key,
    service_description_snapshot,
    service_value_snapshot,
    COUNT(*) AS occurrences
FROM calendar_event_services
GROUP BY
    calendar_event_id,
    COALESCE(service_id, -1),
    service_description_snapshot,
    service_value_snapshot
HAVING COUNT(*) > 1
ORDER BY occurrences DESC, calendar_event_id;
