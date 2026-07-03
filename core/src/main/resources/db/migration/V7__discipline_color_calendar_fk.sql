-- LDP spine wiring for the calendar: events belong to a discipline (nullable —
-- personal one-off events are fine without one). Discipline gets a display
-- color (it plays the role Google Calendar's "calendars" play) and is brought
-- up to the BaseEntity shape (updated_at, version) like every other aggregate.
ALTER TABLE discipline ADD COLUMN color VARCHAR(16) NOT NULL DEFAULT 'BLUE';
ALTER TABLE discipline ADD CONSTRAINT discipline_color_check
    CHECK (color IN ('BLUE', 'GREEN', 'RED', 'YELLOW', 'PURPLE', 'ORANGE', 'GRAY'));
ALTER TABLE discipline ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE discipline ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE calendar_event ADD COLUMN discipline_id UUID REFERENCES discipline (id) ON DELETE SET NULL;
CREATE INDEX calendar_event_discipline_idx ON calendar_event (discipline_id);
