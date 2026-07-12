ALTER TABLE vocab_card ADD COLUMN production_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE vocab_card ADD COLUMN production_alpha DOUBLE PRECISION;
ALTER TABLE vocab_card ADD COLUMN production_beta DOUBLE PRECISION;
ALTER TABLE vocab_card ADD COLUMN production_halflife_hours DOUBLE PRECISION;
ALTER TABLE vocab_card ADD COLUMN production_last_reviewed_at TIMESTAMPTZ;

ALTER TABLE vocab_card ADD CONSTRAINT vocab_card_production_model_check CHECK (
    (production_alpha IS NULL AND production_beta IS NULL
        AND production_halflife_hours IS NULL AND production_last_reviewed_at IS NULL)
    OR
    (production_alpha > 0 AND production_beta > 0
        AND production_halflife_hours > 0 AND production_last_reviewed_at IS NOT NULL)
);

ALTER TABLE vocab_review_log ADD COLUMN direction VARCHAR(16) NOT NULL DEFAULT 'RECOGNITION';
ALTER TABLE vocab_review_log ADD CONSTRAINT vocab_review_direction_check
    CHECK (direction IN ('RECOGNITION', 'PRODUCTION'));

CREATE TABLE vocab_deck_preference (
    id                  UUID        PRIMARY KEY,
    language            VARCHAR(16) NOT NULL,
    deck                VARCHAR(80) NOT NULL,
    production_default  BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT vocab_deck_preference_language_check
        CHECK (language IN ('ENGLISH', 'CHINESE'))
);

CREATE UNIQUE INDEX vocab_deck_preference_identity_idx
    ON vocab_deck_preference (language, lower(deck));

