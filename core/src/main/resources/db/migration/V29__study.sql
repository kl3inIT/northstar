-- Study: a capture-first log of study sessions. Every entry starts as natural
-- language ("làm listening HSK4 25 phút đúng 18/25") parsed by the LLM; this
-- table stores the confirmed result. A mock test is a session with kind=MOCK
-- and a score — no separate table. Skills come from a seeded vocabulary
-- unioned with values already logged (the finance-category pattern), so the
-- taxonomy converges. Deliberately absent: timers, streaks, materials/content.
CREATE TABLE study_session (
    id               UUID          PRIMARY KEY,
    occurred_on      DATE          NOT NULL,
    skill            VARCHAR(64)   NOT NULL,
    kind             VARCHAR(16)   NOT NULL,
    duration_minutes INTEGER,
    score_raw        INTEGER,
    score_max        INTEGER,
    notes            VARCHAR(2000),
    discipline_id    UUID          REFERENCES discipline (id) ON DELETE SET NULL,
    source           VARCHAR(16)   NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    updated_at       TIMESTAMPTZ   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT study_session_kind_check CHECK (kind IN ('PRACTICE', 'MOCK')),
    CONSTRAINT study_session_source_check CHECK (source IN ('CAPTURE', 'ASSISTANT', 'MANUAL')),
    CONSTRAINT study_session_duration_check CHECK (duration_minutes IS NULL OR duration_minutes > 0),
    CONSTRAINT study_session_score_check CHECK (
        (score_raw IS NULL AND score_max IS NULL)
        OR (score_raw IS NOT NULL AND score_max IS NOT NULL
            AND score_max > 0 AND score_raw >= 0 AND score_raw <= score_max)
    )
);

-- Every read path is date-scoped (page range, weekly summary, mock trend).
CREATE INDEX study_session_occurred_idx ON study_session (occurred_on);
