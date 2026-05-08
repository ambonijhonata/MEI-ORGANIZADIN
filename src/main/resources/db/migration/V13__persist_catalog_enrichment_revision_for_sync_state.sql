ALTER TABLE sync_state
    ADD COLUMN IF NOT EXISTS catalog_enrichment_revision_requested BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS catalog_enrichment_revision_applied BIGINT NOT NULL DEFAULT 0;

INSERT INTO sync_state (
    user_id,
    sync_token,
    last_sync_at,
    status,
    error_category,
    error_message,
    catalog_enrichment_revision_requested,
    catalog_enrichment_revision_applied,
    created_at,
    updated_at
)
SELECT DISTINCT
    ce.user_id,
    CAST(NULL AS TEXT),
    CAST(NULL AS TIMESTAMP WITH TIME ZONE),
    CAST('NEVER_SYNCED' AS VARCHAR(50)),
    CAST(NULL AS VARCHAR(100)),
    CAST(NULL AS TEXT),
    CAST(1 AS BIGINT),
    CAST(0 AS BIGINT),
    CAST(NOW() AS TIMESTAMP WITH TIME ZONE),
    CAST(NOW() AS TIMESTAMP WITH TIME ZONE)
FROM calendar_events ce
LEFT JOIN sync_state ss ON ss.user_id = ce.user_id
WHERE ce.google_event_id IS NOT NULL
  AND ce.google_event_id <> ''
  AND ss.user_id IS NULL;

UPDATE sync_state ss
SET catalog_enrichment_revision_requested = GREATEST(ss.catalog_enrichment_revision_requested, 1),
    updated_at = NOW()
WHERE EXISTS (
    SELECT 1
    FROM calendar_events ce
    WHERE ce.user_id = ss.user_id
      AND ce.google_event_id IS NOT NULL
      AND ce.google_event_id <> ''
)
  AND ss.catalog_enrichment_revision_requested = 0;
