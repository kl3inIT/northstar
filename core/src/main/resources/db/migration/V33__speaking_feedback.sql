CREATE TABLE speaking_feedback (
    id                  UUID             PRIMARY KEY,
    submitted_at        TIMESTAMPTZ      NOT NULL,
    question            VARCHAR(1000)    NOT NULL,
    transcript          VARCHAR(8000)    NOT NULL,
    pronunciation       DOUBLE PRECISION,
    fluency             DOUBLE PRECISION,
    prosody             DOUBLE PRECISION,
    content_scores      VARCHAR(1000)    NOT NULL,
    top_errors          VARCHAR(4000)    NOT NULL,
    summary             VARCHAR(4000)    NOT NULL,
    grader_model        VARCHAR(128)     NOT NULL,
    delivery_provider   VARCHAR(64)      NOT NULL,
    provider_revision   VARCHAR(128)     NOT NULL,
    created_at          TIMESTAMPTZ      NOT NULL,
    updated_at          TIMESTAMPTZ      NOT NULL,
    version             BIGINT           NOT NULL DEFAULT 0
);

CREATE INDEX speaking_feedback_submitted_idx ON speaking_feedback (submitted_at);
