-- Semantic search storage (the vector half of hybrid search, see V2 for the
-- keyword half). The table is Spring AI's PgVectorStore layout, NOT a domain
-- table: Flyway owns the DDL (initialize-schema: false, same stance as V12's
-- chat memory) and the columns must match PgVectorStore#initializeSchema
-- exactly — id/content/metadata/embedding, index named spring_ai_vector_index.
-- Note chunks live here with metadata {noteId, slug, title, chunk,
-- noteUpdatedAt}; rows are derived and disposable (rebuilt by the backfill),
-- so there is deliberately no FK to note — deletion is the indexer's job.
--
-- 1536 dims = text-embedding-3-large shortened via the `dimensions` request
-- option (Matryoshka); full 3072 would exceed pgvector's 2000-dim index cap.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE vector_store (
    id        uuid PRIMARY KEY,
    content   text,
    metadata  json,
    embedding vector(1536)
);

CREATE INDEX spring_ai_vector_index ON vector_store USING hnsw (embedding vector_cosine_ops);
