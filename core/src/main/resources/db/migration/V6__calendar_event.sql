-- Calendar events: time-blocked entries (study sessions, classes, appointments),
-- distinct from tasks (due dates). Instants are stored UTC; all_day events span
-- local midnights chosen by the client. rrule is reserved for recurring events
-- (iCalendar RRULE, also what Google Calendar sync will speak) — column exists
-- so recurrence lands without another migration, unmapped until then.
CREATE TABLE calendar_event (
    id         UUID PRIMARY KEY,
    title      VARCHAR(512) NOT NULL,
    notes      TEXT,
    start_at   TIMESTAMPTZ  NOT NULL,
    end_at     TIMESTAMPTZ  NOT NULL,
    all_day    BOOLEAN      NOT NULL DEFAULT FALSE,
    color      VARCHAR(16)  NOT NULL DEFAULT 'BLUE',
    rrule      VARCHAR(512),
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT calendar_event_time_check CHECK (end_at > start_at),
    CONSTRAINT calendar_event_color_check
        CHECK (color IN ('BLUE', 'GREEN', 'RED', 'YELLOW', 'PURPLE', 'ORANGE', 'GRAY'))
);

-- The calendar always reads a visible window (month/week/day): overlap query
-- filters start_at < :to AND end_at > :from.
CREATE INDEX calendar_event_window_idx ON calendar_event (start_at, end_at);
