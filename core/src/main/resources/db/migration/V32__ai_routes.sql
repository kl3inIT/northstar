CREATE TABLE ai_route_setting (
    task VARCHAR(32) PRIMARY KEY,
    gateway_id VARCHAR(64) NOT NULL,
    model_id VARCHAR(255) NOT NULL
);

CREATE TABLE assistant_conversation_route (
    conversation_id VARCHAR(255) PRIMARY KEY,
    gateway_id VARCHAR(64) NOT NULL,
    model_id VARCHAR(255) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
