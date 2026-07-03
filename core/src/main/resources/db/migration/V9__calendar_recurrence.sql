-- Recurring events, GCal semantics subset. The master row keeps its rrule
-- (column exists since V6); each generated occurrence the user deletes
-- ("chỉ buổi này") becomes an exception row keyed by the occurrence's start
-- instant. "Edit this occurrence" = exception row + a standalone event.
CREATE TABLE calendar_event_exception (
    event_id         UUID        NOT NULL REFERENCES calendar_event (id) ON DELETE CASCADE,
    occurrence_start TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, occurrence_start)
);
