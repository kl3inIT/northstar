-- LDP spine wiring for tasks: every action can point at the discipline it
-- trains (nullable — inbox-style tasks are fine without one). project_id comes
-- later with the projects module; tasks then carry both, discipline always.
ALTER TABLE task ADD COLUMN discipline_id UUID REFERENCES discipline (id) ON DELETE SET NULL;
CREATE INDEX task_discipline_idx ON task (discipline_id);
