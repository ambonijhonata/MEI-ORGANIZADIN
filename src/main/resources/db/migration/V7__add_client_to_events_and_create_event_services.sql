-- Add client reference to calendar_events
ALTER TABLE calendar_events ADD COLUMN client_id BIGINT REFERENCES clients(id) ON DELETE SET NULL;
CREATE INDEX idx_calendar_events_client ON calendar_events (client_id);

-- Create junction table for multiple services per event
CREATE TABLE calendar_event_services (
    id BIGSERIAL PRIMARY KEY,
    calendar_event_id BIGINT NOT NULL REFERENCES calendar_events(id) ON DELETE CASCADE,
    service_id BIGINT REFERENCES services(id) ON DELETE SET NULL,
    service_description_snapshot VARCHAR(500) NOT NULL,
    service_value_snapshot NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_event_services_event ON calendar_event_services (calendar_event_id);
CREATE INDEX idx_event_services_service ON calendar_event_services (service_id);
