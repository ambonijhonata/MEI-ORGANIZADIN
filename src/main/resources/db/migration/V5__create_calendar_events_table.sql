CREATE TABLE calendar_events (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    google_event_id VARCHAR(1024) NOT NULL,
    title VARCHAR(1000),
    normalized_title VARCHAR(1000),
    event_start TIMESTAMP WITH TIME ZONE NOT NULL,
    event_end TIMESTAMP WITH TIME ZONE,
    service_id BIGINT REFERENCES services(id) ON DELETE SET NULL,
    service_description_snapshot VARCHAR(500),
    service_value_snapshot NUMERIC(12, 2),
    identified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, google_event_id)
);

CREATE INDEX idx_calendar_events_user_start ON calendar_events (user_id, event_start);
CREATE INDEX idx_calendar_events_user_identified ON calendar_events (user_id, identified);
CREATE INDEX idx_calendar_events_service ON calendar_events (service_id);
