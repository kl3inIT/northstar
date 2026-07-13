CREATE TABLE vocab_audio_attempt (
    id                     UUID PRIMARY KEY,
    vocab_card_id          UUID NOT NULL REFERENCES vocab_card(id) ON DELETE CASCADE,
    mode                   VARCHAR(16) NOT NULL,
    reference_text         VARCHAR(2000) NOT NULL,
    recognized_text        VARCHAR(2000),
    accuracy               DOUBLE PRECISION,
    fluency                DOUBLE PRECISION,
    prosody                DOUBLE PRECISION,
    words_json             TEXT,
    provider_id            VARCHAR(64),
    provider_revision      VARCHAR(128),
    dictation_answer       VARCHAR(2000),
    dictation_diff         TEXT,
    recording              BYTEA,
    recording_mime_type    VARCHAR(64),
    duration_seconds       DOUBLE PRECISION,
    recording_expires_at   TIMESTAMPTZ,
    recording_pinned       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,
    version                BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT vocab_audio_attempt_mode_check
        CHECK (mode IN ('WORD', 'SHADOWING', 'DICTATION')),
    CONSTRAINT vocab_audio_attempt_accuracy_check
        CHECK (accuracy IS NULL OR (accuracy >= 0 AND accuracy <= 100)),
    CONSTRAINT vocab_audio_attempt_fluency_check
        CHECK (fluency IS NULL OR (fluency >= 0 AND fluency <= 100)),
    CONSTRAINT vocab_audio_attempt_prosody_check
        CHECK (prosody IS NULL OR (prosody >= 0 AND prosody <= 100)),
    CONSTRAINT vocab_audio_attempt_recording_check
        CHECK ((recording IS NULL AND recording_mime_type IS NULL AND recording_expires_at IS NULL)
            OR (recording IS NOT NULL AND recording_mime_type IS NOT NULL AND recording_expires_at IS NOT NULL)),
    CONSTRAINT vocab_audio_attempt_mode_data_check
        CHECK ((mode IN ('WORD', 'SHADOWING')
                AND accuracy IS NOT NULL AND fluency IS NOT NULL
                AND provider_id IS NOT NULL AND provider_revision IS NOT NULL
                AND dictation_answer IS NULL AND dictation_diff IS NULL)
            OR (mode = 'DICTATION'
                AND accuracy IS NOT NULL
                AND fluency IS NULL AND prosody IS NULL
                AND provider_id IS NULL AND provider_revision IS NULL
                AND dictation_answer IS NOT NULL AND dictation_diff IS NOT NULL
                AND recording IS NULL))
);

CREATE INDEX vocab_audio_attempt_card_created_idx
    ON vocab_audio_attempt (vocab_card_id, created_at DESC);
CREATE INDEX vocab_audio_attempt_retention_idx
    ON vocab_audio_attempt (recording_expires_at)
    WHERE recording IS NOT NULL AND recording_pinned = FALSE;
