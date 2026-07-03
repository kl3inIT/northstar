-- Task: the todo store. One table, many views later (list / kanban / calendar):
-- status drives kanban columns, due_date/due_time drive Today and calendar slots.
CREATE TABLE task (
    id           UUID PRIMARY KEY,
    title        VARCHAR(512)  NOT NULL,
    notes        TEXT,
    status       VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    due_date     DATE,
    due_time     TIME,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL,
    updated_at   TIMESTAMPTZ   NOT NULL,
    CONSTRAINT task_status_check CHECK (status IN ('OPEN', 'DONE'))
);

-- Today/upcoming queries filter open tasks by due date.
CREATE INDEX task_open_due_idx ON task (due_date) WHERE status = 'OPEN';
