CREATE TABLE ai_gateway_setting (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    base_url VARCHAR(500) NOT NULL,
    api_key_ciphertext BYTEA NOT NULL,
    models TEXT NOT NULL DEFAULT '',
    discover_models BOOLEAN NOT NULL DEFAULT TRUE,
    timeout_seconds INTEGER NOT NULL DEFAULT 60,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ai_gateway_timeout_range CHECK (timeout_seconds BETWEEN 5 AND 300)
);
