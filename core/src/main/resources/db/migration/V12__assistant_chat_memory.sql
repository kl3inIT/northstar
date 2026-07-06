-- Conversation memory for the in-app assistant. Schema is EXACTLY what Spring
-- AI's JdbcChatMemoryRepository expects (its bundled schema-postgresql.sql,
-- spring-ai-model-chat-memory-repository-jdbc 2.0.0) — Flyway owns the schema,
-- so the repository's own initializer stays off (initialize-schema: never).
CREATE TABLE spring_ai_chat_memory (
    conversation_id VARCHAR(36) NOT NULL,
    content         TEXT        NOT NULL,
    type            VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp"     TIMESTAMP   NOT NULL,
    sequence_id     BIGINT      NOT NULL
);

CREATE INDEX spring_ai_chat_memory_conversation_id_timestamp_idx
    ON spring_ai_chat_memory (conversation_id, "timestamp");

CREATE INDEX spring_ai_chat_memory_conversation_id_sequence_id_idx
    ON spring_ai_chat_memory (conversation_id, sequence_id);
