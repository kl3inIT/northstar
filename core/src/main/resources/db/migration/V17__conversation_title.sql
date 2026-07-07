-- LLM-generated short titles for assistant conversations, kept in a table of our
-- own so spring_ai_chat_memory stays EXACTLY Spring AI's schema (V12). One row per
-- conversation; absence means "not titled yet" and /conversations falls back to the
-- first user message. conversation_id is the client-supplied key (see the assistant).
CREATE TABLE northstar_conversation_title (
    conversation_id VARCHAR(36)  NOT NULL PRIMARY KEY,
    title           VARCHAR(120) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT now()
);
