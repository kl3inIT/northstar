-- Northstar notes are mixed English/Vietnamese. PostgreSQL's `english`
-- configuration stems and removes English stop words, but it is the wrong
-- lexical baseline for a personal multilingual vault. `simple` keeps tokens
-- as written and lets semantic search carry paraphrase matching.

DROP INDEX IF EXISTS idx_note_search_tsv;

ALTER TABLE note DROP COLUMN search_tsv;

ALTER TABLE note
    ADD COLUMN search_tsv tsvector
        GENERATED ALWAYS AS (
            setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
            setweight(to_tsvector('simple', coalesce(content_markdown, '')), 'B')
        ) STORED;

CREATE INDEX idx_note_search_tsv ON note USING gin (search_tsv);
