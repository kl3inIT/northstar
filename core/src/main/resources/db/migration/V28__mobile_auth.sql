CREATE TABLE mobile_refresh_token (
    id UUID PRIMARY KEY,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_mobile_refresh_token_family
    ON mobile_refresh_token (family_id);

CREATE INDEX idx_mobile_refresh_token_expires
    ON mobile_refresh_token (expires_at);
