-- Writing tutor: LLM-graded essay feedback with a persistent error history.
-- Each grading stores the essay, per-criterion bands with justification
-- (criteria JSON), the extracted recurring errors (top_errors JSON — the
-- learner error corpus the next grading compares against), and the pinned
-- grader model id so estimates stay comparable across model upgrades. The
-- overall band is an UNOFFICIAL estimate range (min..max), never a single
-- authoritative score. rubric names the prompt resource used — a plug-in
-- point for future subjects, not an IELTS-specific column.
CREATE TABLE writing_feedback (
    id             UUID             PRIMARY KEY,
    submitted_at   TIMESTAMPTZ      NOT NULL,
    task_label     VARCHAR(255)     NOT NULL,
    rubric         VARCHAR(64)      NOT NULL,
    essay_markdown VARCHAR(20000)   NOT NULL,
    word_count     INTEGER          NOT NULL,
    overall_min    DOUBLE PRECISION NOT NULL,
    overall_max    DOUBLE PRECISION NOT NULL,
    criteria       VARCHAR(8000)    NOT NULL,
    top_errors     VARCHAR(4000)    NOT NULL,
    summary        VARCHAR(4000)    NOT NULL,
    grader_model   VARCHAR(64)      NOT NULL,
    created_at     TIMESTAMPTZ      NOT NULL,
    updated_at     TIMESTAMPTZ      NOT NULL,
    version        BIGINT           NOT NULL DEFAULT 0,
    CONSTRAINT writing_overall_check CHECK (overall_min > 0 AND overall_max >= overall_min)
);

CREATE INDEX writing_feedback_submitted_idx ON writing_feedback (submitted_at);
