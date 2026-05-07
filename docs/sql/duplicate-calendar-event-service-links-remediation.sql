-- Use this script as an operational fallback when you want to diagnose or clean duplicates manually.
-- V12__prevent_duplicate_calendar_event_service_links.sql now performs the same cleanup automatically.

BEGIN;

WITH duplicate_groups AS (
    SELECT
        calendar_event_id,
        service_id,
        service_description_snapshot,
        service_value_snapshot,
        COUNT(*) AS occurrences
    FROM calendar_event_services
    GROUP BY
        calendar_event_id,
        service_id,
        service_description_snapshot,
        service_value_snapshot
    HAVING COUNT(*) > 1
),
ranked_by_service AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY calendar_event_id, service_id
            ORDER BY id
        ) AS rn
    FROM calendar_event_services
    WHERE service_id IS NOT NULL
),
ranked_by_snapshot AS (
    SELECT
        id,
        ROW_NUMBER() OVER (
            PARTITION BY
                calendar_event_id,
                service_description_snapshot,
                service_value_snapshot
            ORDER BY id
        ) AS rn
    FROM calendar_event_services
    WHERE service_id IS NULL
),
rows_to_delete AS (
    SELECT id FROM ranked_by_service WHERE rn > 1
    UNION
    SELECT id FROM ranked_by_snapshot WHERE rn > 1
)
DELETE FROM calendar_event_services ces
USING rows_to_delete r
WHERE ces.id = r.id;

COMMIT;

-- Diagnostic query: run before cleanup to measure the current scope and after cleanup to verify it is empty.
SELECT
    calendar_event_id,
    service_id,
    service_description_snapshot,
    service_value_snapshot,
    COUNT(*) AS occurrences
FROM calendar_event_services
GROUP BY
    calendar_event_id,
    service_id,
    service_description_snapshot,
    service_value_snapshot
HAVING COUNT(*) > 1
ORDER BY occurrences DESC, calendar_event_id;

-- Additional verification aligned with the unique index on non-null service_id.
SELECT
    calendar_event_id,
    service_id,
    COUNT(*) AS occurrences
FROM calendar_event_services
WHERE service_id IS NOT NULL
GROUP BY
    calendar_event_id,
    service_id
HAVING COUNT(*) > 1
ORDER BY occurrences DESC, calendar_event_id, service_id;
