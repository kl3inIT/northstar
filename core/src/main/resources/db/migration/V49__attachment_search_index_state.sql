-- Search owns the lifecycle of derived attachment text/vectors. Attachment
-- bytes remain immutable and authoritative; this row can be deleted/rebuilt.
CREATE TABLE attachment_search_index_state (
    attachment_id UUID PRIMARY KEY REFERENCES attachment(id) ON DELETE CASCADE,
    status        VARCHAR(16)  NOT NULL,
    content_hash  VARCHAR(64),
    error_code    VARCHAR(64),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT attachment_search_index_status_check
        CHECK (status IN ('PENDING', 'PROCESSING', 'READY', 'FAILED', 'UNSUPPORTED'))
);

CREATE INDEX attachment_search_index_status_idx
    ON attachment_search_index_state (status, updated_at);
