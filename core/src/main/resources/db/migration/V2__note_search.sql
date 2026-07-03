-- Keyword full-text search over notes (Phase 1). A generated tsvector column,
-- kept in sync by Postgres, weights the title (A) above the body (B). The GIN
-- index makes `search_tsv @@ websearch_to_tsquery(...)` fast. This column is NOT
-- mapped by JPA (ddl-auto: validate only checks mapped columns), so the entity
-- stays clean; the search query reads it via a native query.
--
-- Semantic/vector search is a later, separate addition (pgvector + note_chunk);
-- this is the keyword half of the eventual hybrid. The in-note search box uses
-- this; only the chat/"Ask Northstar" retrieval fuses in vectors.

ALTER TABLE note
    ADD COLUMN search_tsv tsvector
        GENERATED ALWAYS AS (
            setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
            setweight(to_tsvector('english', coalesce(content_markdown, '')), 'B')
        ) STORED;

CREATE INDEX idx_note_search_tsv ON note USING gin (search_tsv);
