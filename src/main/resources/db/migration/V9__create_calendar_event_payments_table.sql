CREATE TABLE calendar_event_payments (
    id BIGSERIAL PRIMARY KEY,
    calendar_event_id BIGINT NOT NULL REFERENCES calendar_events(id) ON DELETE CASCADE,
    payment_type VARCHAR(20) NOT NULL,
    amount NUMERIC(12,2) NOT NULL,
    value_total BOOLEAN NOT NULL DEFAULT FALSE,
    paid_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_calendar_event_payments_event_id
    ON calendar_event_payments(calendar_event_id);
