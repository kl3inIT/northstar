-- One client action may be retried by the browser or proxy, but it must never
-- execute assistant tools twice. Keep this outside Spring AI's vendor-owned
-- memory table and claim the key atomically before starting a streamed turn.
CREATE TABLE northstar_assistant_turn (
    id              UUID         NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(36)  NOT NULL,
    client_turn_id  VARCHAR(160) NOT NULL,
    turn_id         VARCHAR(36)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX northstar_assistant_turn_client_idx
    ON northstar_assistant_turn (conversation_id, client_turn_id);

CREATE UNIQUE INDEX northstar_assistant_turn_id_idx
    ON northstar_assistant_turn (turn_id);

CREATE INDEX northstar_assistant_turn_conversation_idx
    ON northstar_assistant_turn (conversation_id, created_at);
