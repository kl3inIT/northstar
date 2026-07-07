-- File storage, memos-style: one immutable metadata row per file; WHERE the
-- bytes live is a per-row storage_type. DATABASE (bytea in `data`) is the only
-- backend today — one pg_dump covers files and data alike at this scale.
-- LOCAL/S3 stay open as values so an external backend later is a new column
-- value + reference (path/key), never a schema migration. Files are
-- content-addressed by sha256 for dedupe: re-uploading the same bytes returns
-- the existing row, which also makes ids safe to cache forever.
CREATE TABLE attachment (
    id           UUID PRIMARY KEY,
    filename     VARCHAR(255) NOT NULL,
    mime_type    VARCHAR(255) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    -- VARCHAR, not CHAR: Postgres reports CHAR as bpchar and ddl-auto: validate
    -- rejects it against Hibernate's varchar mapping.
    sha256       VARCHAR(64)  NOT NULL,
    storage_type VARCHAR(16)  NOT NULL DEFAULT 'DATABASE',
    reference    TEXT,
    data         BYTEA,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT attachment_storage_type_check CHECK (storage_type IN ('DATABASE', 'LOCAL', 'S3')),
    CONSTRAINT attachment_data_present_check CHECK (storage_type <> 'DATABASE' OR data IS NOT NULL)
);

CREATE UNIQUE INDEX attachment_sha256_idx ON attachment (sha256);
