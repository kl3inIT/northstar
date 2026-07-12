CREATE TABLE speech_asset (
    id            UUID PRIMARY KEY,
    cache_key     VARCHAR(64)  NOT NULL,
    text_hash     VARCHAR(64)  NOT NULL,
    text_length   INTEGER      NOT NULL,
    gateway_id    VARCHAR(64)  NOT NULL,
    target_id     VARCHAR(255) NOT NULL,
    locale        VARCHAR(35)  NOT NULL,
    format        VARCHAR(16)  NOT NULL,
    mime_type     VARCHAR(100) NOT NULL,
    attachment_id UUID         NOT NULL REFERENCES attachment(id),
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT speech_asset_text_length_check CHECK (text_length BETWEEN 1 AND 4096),
    CONSTRAINT speech_asset_format_check CHECK (format IN ('mp3'))
);

CREATE UNIQUE INDEX speech_asset_cache_key_idx ON speech_asset (cache_key);
CREATE INDEX speech_asset_attachment_idx ON speech_asset (attachment_id);
