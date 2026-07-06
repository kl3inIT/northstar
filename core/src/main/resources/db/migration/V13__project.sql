-- Projects: the LDP execution layer — a large, staged piece of work under a
-- discipline ("Chevening application", "IELTS exam Sep 2026"), as opposed to a
-- task's single action. Milestones are the stages a project moves through;
-- progress is derived (done milestones / total), never stored. A scholarship
-- row will later own a project 1-1 once the scholarship module lands.
CREATE TABLE project (
    id            UUID PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    notes         TEXT,
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    discipline_id UUID REFERENCES discipline (id) ON DELETE SET NULL,
    start_date    DATE,
    target_date   DATE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT project_status_check CHECK (status IN ('ACTIVE', 'DONE'))
);

CREATE INDEX project_discipline_idx ON project (discipline_id);

CREATE TABLE project_milestone (
    id         UUID PRIMARY KEY,
    project_id UUID         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,
    due_date   DATE,
    done_at    TIMESTAMPTZ,
    sort_order INTEGER      NOT NULL DEFAULT 0
);

CREATE INDEX project_milestone_project_idx ON project_milestone (project_id);

-- A task can belong to one project (the project's agenda); a task without one
-- keeps living as before.
ALTER TABLE task ADD COLUMN project_id UUID REFERENCES project (id) ON DELETE SET NULL;
CREATE INDEX task_project_idx ON task (project_id);
