-- Replace the single-user Ebisu state with independent FSRS-6 scheduling cards.
-- Vocabulary content and direction preferences remain; scheduling history is
-- intentionally reset for this deployment.
CREATE TABLE vocab_scheduling_card (
    id                 UUID        PRIMARY KEY,
    vocab_card_id      UUID        NOT NULL REFERENCES vocab_card (id) ON DELETE CASCADE,
    direction          VARCHAR(16) NOT NULL,
    state              VARCHAR(16) NOT NULL,
    learning_step      INTEGER,
    stability_days     DOUBLE PRECISION,
    difficulty         DOUBLE PRECISION,
    due_at             TIMESTAMPTZ NOT NULL,
    last_reviewed_at   TIMESTAMPTZ,
    lapse_count        INTEGER     NOT NULL DEFAULT 0,
    leech              BOOLEAN     NOT NULL DEFAULT FALSE,
    buried_until       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT vocab_scheduling_direction_check
        CHECK (direction IN ('RECOGNITION', 'PRODUCTION')),
    CONSTRAINT vocab_scheduling_state_check
        CHECK (state IN ('LEARNING', 'REVIEW', 'RELEARNING')),
    CONSTRAINT vocab_scheduling_step_check
        CHECK (learning_step IS NULL OR learning_step >= 0),
    CONSTRAINT vocab_scheduling_memory_check CHECK (
        (stability_days IS NULL AND difficulty IS NULL)
        OR (stability_days > 0 AND difficulty >= 1 AND difficulty <= 10)
    ),
    CONSTRAINT vocab_scheduling_lapse_check CHECK (lapse_count >= 0)
);

CREATE UNIQUE INDEX vocab_scheduling_identity_idx
    ON vocab_scheduling_card (vocab_card_id, direction);
CREATE INDEX vocab_scheduling_due_idx
    ON vocab_scheduling_card (due_at, buried_until);

INSERT INTO vocab_scheduling_card (
    id, vocab_card_id, direction, state, learning_step, due_at,
    lapse_count, leech, created_at, updated_at, version
)
SELECT md5(id::text || ':RECOGNITION')::uuid, id, 'RECOGNITION', 'LEARNING', 0,
       CURRENT_TIMESTAMP, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM vocab_card;

INSERT INTO vocab_scheduling_card (
    id, vocab_card_id, direction, state, learning_step, due_at,
    lapse_count, leech, created_at, updated_at, version
)
SELECT md5(id::text || ':PRODUCTION')::uuid, id, 'PRODUCTION', 'LEARNING', 0,
       CURRENT_TIMESTAMP, 0, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0
FROM vocab_card
WHERE production_enabled = TRUE;

DROP TABLE vocab_review_log;

CREATE TABLE vocab_review_log (
    id                      UUID        PRIMARY KEY,
    scheduling_card_id      UUID        NOT NULL REFERENCES vocab_scheduling_card (id) ON DELETE CASCADE,
    card_id                 UUID        NOT NULL REFERENCES vocab_card (id) ON DELETE CASCADE,
    direction               VARCHAR(16) NOT NULL,
    reviewed_at             TIMESTAMPTZ NOT NULL,
    rating                  VARCHAR(8)  NOT NULL,
    source                  VARCHAR(16) NOT NULL,
    elapsed_days            DOUBLE PRECISION NOT NULL,
    lapse                   BOOLEAN     NOT NULL,
    state_before            VARCHAR(16) NOT NULL,
    step_before             INTEGER,
    stability_before        DOUBLE PRECISION,
    difficulty_before       DOUBLE PRECISION,
    due_before              TIMESTAMPTZ NOT NULL,
    last_review_before      TIMESTAMPTZ,
    state_after             VARCHAR(16) NOT NULL,
    step_after              INTEGER,
    stability_after         DOUBLE PRECISION NOT NULL,
    difficulty_after        DOUBLE PRECISION NOT NULL,
    due_after               TIMESTAMPTZ NOT NULL,
    last_review_after       TIMESTAMPTZ NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL,
    updated_at              TIMESTAMPTZ NOT NULL,
    version                 BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT vocab_review_rating_check CHECK (rating IN ('AGAIN', 'HARD', 'GOOD', 'EASY')),
    CONSTRAINT vocab_review_source_check CHECK (source IN ('BRIEF', 'CHAT', 'MANUAL')),
    CONSTRAINT vocab_review_direction_check CHECK (direction IN ('RECOGNITION', 'PRODUCTION')),
    CONSTRAINT vocab_review_state_before_check
        CHECK (state_before IN ('LEARNING', 'REVIEW', 'RELEARNING')),
    CONSTRAINT vocab_review_state_after_check
        CHECK (state_after IN ('LEARNING', 'REVIEW', 'RELEARNING')),
    CONSTRAINT vocab_review_elapsed_check CHECK (elapsed_days >= 0)
);

CREATE INDEX vocab_review_log_card_idx ON vocab_review_log (card_id, reviewed_at);
CREATE INDEX vocab_review_log_schedule_idx
    ON vocab_review_log (scheduling_card_id, reviewed_at);

ALTER TABLE vocab_card
    DROP CONSTRAINT vocab_card_model_check,
    DROP CONSTRAINT vocab_card_production_model_check,
    DROP COLUMN alpha,
    DROP COLUMN beta,
    DROP COLUMN halflife_hours,
    DROP COLUMN last_reviewed_at,
    DROP COLUMN production_alpha,
    DROP COLUMN production_beta,
    DROP COLUMN production_halflife_hours,
    DROP COLUMN production_last_reviewed_at;

