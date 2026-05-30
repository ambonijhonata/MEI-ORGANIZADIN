ALTER TABLE calendar_events
    ALTER COLUMN google_event_id DROP NOT NULL;

ALTER TABLE calendar_events
    ADD COLUMN source VARCHAR(20);

UPDATE calendar_events
SET source = 'GOOGLE'
WHERE source IS NULL;

ALTER TABLE calendar_events
    ALTER COLUMN source SET NOT NULL;
