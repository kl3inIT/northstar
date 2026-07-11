-- Vocabulary memory: capture-first cards reviewed through chat and the
-- morning brief. NO due dates by design — each card carries an Ebisu memory
-- model (alpha/beta Beta-distribution parameters pinned at halflife_hours) and
-- consumers ask "which N cards are most at risk right now", so a lapse of any
-- length never builds a review backlog. Language specifics (pinyin, examples,
-- audio) live in the metadata JSON text, never as columns — the schema stays
-- generic for future subjects. The review log is append-only and carries the
-- fields an FSRS-style optimizer would need, as algorithm-migration insurance.
CREATE TABLE vocab_card (
    id               UUID             PRIMARY KEY,
    front            VARCHAR(255)     NOT NULL,
    back             VARCHAR(1000)    NOT NULL,
    metadata         VARCHAR(4000),
    discipline_id    UUID             REFERENCES discipline (id) ON DELETE SET NULL,
    alpha            DOUBLE PRECISION NOT NULL,
    beta             DOUBLE PRECISION NOT NULL,
    halflife_hours   DOUBLE PRECISION NOT NULL,
    last_reviewed_at TIMESTAMPTZ      NOT NULL,
    suspended        BOOLEAN          NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ      NOT NULL,
    updated_at       TIMESTAMPTZ      NOT NULL,
    version          BIGINT           NOT NULL DEFAULT 0,
    CONSTRAINT vocab_card_model_check CHECK (alpha > 0 AND beta > 0 AND halflife_hours > 0)
);

CREATE TABLE vocab_review_log (
    id               UUID             PRIMARY KEY,
    card_id          UUID             NOT NULL REFERENCES vocab_card (id) ON DELETE CASCADE,
    reviewed_at      TIMESTAMPTZ      NOT NULL,
    success          DOUBLE PRECISION NOT NULL,
    rating           VARCHAR(8),
    elapsed_hours    DOUBLE PRECISION NOT NULL,
    source           VARCHAR(16)      NOT NULL,
    alpha_before     DOUBLE PRECISION NOT NULL,
    beta_before      DOUBLE PRECISION NOT NULL,
    halflife_before  DOUBLE PRECISION NOT NULL,
    alpha_after      DOUBLE PRECISION NOT NULL,
    beta_after       DOUBLE PRECISION NOT NULL,
    halflife_after   DOUBLE PRECISION NOT NULL,
    created_at       TIMESTAMPTZ      NOT NULL,
    updated_at       TIMESTAMPTZ      NOT NULL,
    version          BIGINT           NOT NULL DEFAULT 0,
    CONSTRAINT vocab_review_success_check CHECK (success >= 0 AND success <= 1),
    CONSTRAINT vocab_review_rating_check CHECK (
        rating IS NULL OR rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY')),
    CONSTRAINT vocab_review_source_check CHECK (source IN ('BRIEF', 'CHAT', 'MANUAL'))
);

CREATE INDEX vocab_review_log_card_idx ON vocab_review_log (card_id, reviewed_at);
