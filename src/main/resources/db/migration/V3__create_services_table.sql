CREATE TABLE services (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    description VARCHAR(500) NOT NULL,
    normalized_description VARCHAR(500) NOT NULL,
    value NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, normalized_description)
);

CREATE INDEX idx_services_user_id ON services (user_id);
CREATE INDEX idx_services_user_normalized ON services (user_id, normalized_description);
