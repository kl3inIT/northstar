-- One optional runtime override. No row means application configuration wins.
CREATE TABLE web_research_setting (
    id                 UUID        PRIMARY KEY,
    enabled            BOOLEAN     NOT NULL,
    search_provider_id VARCHAR(64) NOT NULL,
    page_reader_id     VARCHAR(64) NOT NULL,
    fallback_enabled   BOOLEAN     NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT      NOT NULL DEFAULT 0
);
