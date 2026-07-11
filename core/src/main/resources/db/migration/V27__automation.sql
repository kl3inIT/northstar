CREATE TABLE automation_definition (
    id                      UUID         PRIMARY KEY,
    type                    VARCHAR(64)  NOT NULL,
    name                    VARCHAR(160) NOT NULL,
    enabled                 BOOLEAN      NOT NULL,
    trigger_kind            VARCHAR(16)  NOT NULL,
    local_time              TIME         NOT NULL,
    days_of_week            VARCHAR(96)  NOT NULL,
    timezone_id             VARCHAR(64)  NOT NULL,
    catch_up_window_minutes INTEGER      NOT NULL,
    workflow_config         JSONB        NOT NULL,
    config_version          INTEGER      NOT NULL,
    schedule_version        BIGINT       NOT NULL,
    synced_schedule_version BIGINT       NOT NULL DEFAULT 0,
    deleted_at              TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    version                 BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT automation_trigger_kind_check
        CHECK (trigger_kind IN ('DAILY', 'WEEKLY')),
    CONSTRAINT automation_catch_up_window_check
        CHECK (catch_up_window_minutes BETWEEN 0 AND 1440),
    CONSTRAINT automation_config_version_check CHECK (config_version > 0),
    CONSTRAINT automation_schedule_version_check CHECK (schedule_version > 0)
);

CREATE INDEX automation_definition_active_idx
    ON automation_definition (deleted_at, enabled, updated_at);

CREATE TABLE automation_run (
    id             UUID         PRIMARY KEY,
    automation_id  UUID         NOT NULL REFERENCES automation_definition(id),
    scheduled_for  TIMESTAMPTZ  NOT NULL,
    run_kind       VARCHAR(16)  NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    attempt        INTEGER      NOT NULL,
    started_at     TIMESTAMPTZ,
    finished_at    TIMESTAMPTZ,
    error_code     VARCHAR(64),
    error_message  VARCHAR(2000),
    output_type    VARCHAR(64),
    output_id      UUID,
    metrics        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL,
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT automation_run_identity_unique UNIQUE (automation_id, scheduled_for),
    CONSTRAINT automation_run_kind_check CHECK (run_kind IN ('SCHEDULED', 'MANUAL')),
    CONSTRAINT automation_run_status_check
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED')),
    CONSTRAINT automation_run_attempt_check CHECK (attempt >= 0)
);

CREATE INDEX automation_run_history_idx
    ON automation_run (automation_id, scheduled_for DESC);
CREATE INDEX automation_run_queue_idx
    ON automation_run (status, scheduled_for) WHERE status = 'QUEUED';

-- Infrastructure projection owned by db-scheduler 16.x. Application code must
-- use SchedulerClient and never mutate this table directly.
CREATE TABLE scheduled_tasks (
    task_name           TEXT        NOT NULL,
    task_instance       TEXT        NOT NULL,
    task_data           BYTEA,
    execution_time      TIMESTAMPTZ NOT NULL,
    picked              BOOLEAN     NOT NULL,
    picked_by           TEXT,
    last_success        TIMESTAMPTZ,
    last_failure        TIMESTAMPTZ,
    consecutive_failures INTEGER,
    last_heartbeat      TIMESTAMPTZ,
    version             BIGINT      NOT NULL,
    priority            SMALLINT,
    PRIMARY KEY (task_name, task_instance)
);

CREATE INDEX scheduled_tasks_execution_time_idx ON scheduled_tasks (execution_time);
CREATE INDEX scheduled_tasks_last_heartbeat_idx ON scheduled_tasks (last_heartbeat);
CREATE INDEX scheduled_tasks_priority_execution_time_idx
    ON scheduled_tasks (priority DESC, execution_time ASC);
