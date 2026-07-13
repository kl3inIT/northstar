CREATE TABLE habit (
    id UUID PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    cue VARCHAR(255),
    notes TEXT,
    color VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT habit_color_check CHECK (color IN ('BLUE', 'GREEN', 'RED', 'YELLOW', 'PURPLE', 'ORANGE', 'GRAY')),
    CONSTRAINT habit_status_check CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE habit_schedule (
    id UUID PRIMARY KEY,
    habit_id UUID NOT NULL REFERENCES habit(id) ON DELETE CASCADE,
    effective_from DATE NOT NULL,
    effective_until DATE,
    frequency_type VARCHAR(24) NOT NULL,
    days_mask SMALLINT NOT NULL,
    weekly_target SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT habit_schedule_dates_check CHECK (effective_until IS NULL OR effective_until >= effective_from),
    CONSTRAINT habit_schedule_frequency_check CHECK (frequency_type IN ('ON_DAYS', 'WEEKLY_TARGET')),
    CONSTRAINT habit_schedule_days_check CHECK (days_mask BETWEEN 0 AND 127),
    CONSTRAINT habit_schedule_target_check CHECK (weekly_target BETWEEN 1 AND 7),
    CONSTRAINT habit_schedule_shape_check CHECK (
        (frequency_type = 'ON_DAYS' AND days_mask > 0)
        OR (frequency_type = 'WEEKLY_TARGET' AND days_mask = 0)
    ),
    UNIQUE (habit_id, effective_from)
);

CREATE INDEX habit_schedule_lookup_idx
    ON habit_schedule (habit_id, effective_from, effective_until);

CREATE TABLE habit_check_in (
    id UUID PRIMARY KEY,
    habit_id UUID NOT NULL REFERENCES habit(id) ON DELETE CASCADE,
    local_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT habit_check_in_status_check CHECK (status IN ('DONE', 'EXCUSED')),
    UNIQUE (habit_id, local_date)
);

CREATE INDEX habit_check_in_range_idx ON habit_check_in (habit_id, local_date);

CREATE TABLE habit_pause (
    id UUID PRIMARY KEY,
    habit_id UUID NOT NULL REFERENCES habit(id) ON DELETE CASCADE,
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT habit_pause_dates_check CHECK (end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX habit_pause_lookup_idx ON habit_pause (habit_id, start_date, end_date);
CREATE UNIQUE INDEX habit_pause_one_open_idx ON habit_pause (habit_id) WHERE end_date IS NULL;
