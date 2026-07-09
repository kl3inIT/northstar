-- Persist assistant tool-call parts outside Spring AI's chat-memory table, so
-- the UI can replay workflow/chain-of-thought summaries after a reload without
-- mutating the vendor-owned spring_ai_chat_memory schema.
CREATE TABLE northstar_assistant_tool_trace (
    id              UUID        NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    turn_id         VARCHAR(36) NOT NULL,
    sequence_index  INTEGER     NOT NULL,
    tool_call_id    VARCHAR(160) NOT NULL,
    tool_name       VARCHAR(160) NOT NULL,
    state           VARCHAR(32) NOT NULL,
    input_json      JSONB,
    output_json     JSONB,
    error_text      TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT northstar_assistant_tool_trace_state_check
        CHECK (state IN (
            'input-streaming',
            'input-available',
            'output-available',
            'output-error'
        ))
);

CREATE UNIQUE INDEX northstar_assistant_tool_trace_call_idx
    ON northstar_assistant_tool_trace (conversation_id, tool_call_id);

CREATE INDEX northstar_assistant_tool_trace_conversation_idx
    ON northstar_assistant_tool_trace (conversation_id, created_at, sequence_index);
