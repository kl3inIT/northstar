-- Northstar initial schema.
-- PostgreSQL is the source of truth. Note bodies are Markdown; wiki links are
-- derived into note_link for backlinks/graph. The Life -> Disciplines spine
-- (LDP) is present from day one so other modules can FK to a discipline.
--
-- pgvector is intentionally NOT enabled yet; semantic search is added later
-- (CREATE EXTENSION vector; + note_chunk table). Keyword/fuzzy search first.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- --- Methodology spine (discipline module) ------------------------------------

CREATE TABLE life_goal (
    id               UUID PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    target_date      DATE,
    success_criteria TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE discipline (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    life_goal_id    UUID REFERENCES life_goal (id) ON DELETE SET NULL,
    ikigai          TEXT[] NOT NULL DEFAULT '{}',   -- subset of {love, world, money, skill}
    weekly_budget_minutes INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- --- Notes / Knowledge Base (note module) -------------------------------------

CREATE TABLE note (
    id               UUID PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    slug             VARCHAR(255) NOT NULL UNIQUE,
    content_markdown TEXT NOT NULL DEFAULT '',
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_note_title_trgm ON note USING gin (title gin_trgm_ops);

-- Derived from wiki links in note.content_markdown. target_note_id is null while
-- the linked note does not exist yet (unresolved link keeps the raw title).
CREATE TABLE note_link (
    id             UUID PRIMARY KEY,
    source_note_id UUID NOT NULL REFERENCES note (id) ON DELETE CASCADE,
    target_note_id UUID REFERENCES note (id) ON DELETE CASCADE,
    target_title   VARCHAR(255) NOT NULL,
    UNIQUE (source_note_id, target_title)
);

CREATE INDEX idx_note_link_target ON note_link (target_note_id);
