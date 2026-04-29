CREATE TABLE refresh_session_tokens (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL UNIQUE,
    issued_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    last_used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(64),
    replaced_by_token_id UUID REFERENCES refresh_session_tokens(id),
    device_id VARCHAR(128),
    app_version VARCHAR(64),
    created_ip VARCHAR(64),
    created_user_agent VARCHAR(512)
);

CREATE INDEX idx_refresh_session_tokens_user_id ON refresh_session_tokens(user_id);
CREATE INDEX idx_refresh_session_tokens_expires_at ON refresh_session_tokens(expires_at);
