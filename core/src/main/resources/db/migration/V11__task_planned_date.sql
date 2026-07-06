-- Things-3 do-vs-due split: planned_date is the day the user intends to WORK on
-- the task ("do"); due_date stays the hard deadline ("due"). Today shows open
-- tasks with due_date <= today OR planned_date <= today (plans roll forward);
-- Overdue keeps meaning deadline-late only.
ALTER TABLE task ADD COLUMN planned_date DATE;

-- Today rolls open plans forward (planned_date <= today).
CREATE INDEX task_open_planned_idx ON task (planned_date) WHERE status = 'OPEN';
