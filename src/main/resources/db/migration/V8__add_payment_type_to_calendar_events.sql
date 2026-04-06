ALTER TABLE calendar_events
ADD COLUMN payment_type VARCHAR(20);

CREATE INDEX idx_calendar_events_payment_type ON calendar_events (payment_type);
