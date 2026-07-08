-- Notes can belong to one primary project for project-context views. The wiki
-- graph remains many-to-many; this FK is only the note's main execution context.
ALTER TABLE note
    ADD COLUMN project_id UUID REFERENCES project (id) ON DELETE SET NULL;

CREATE INDEX note_project_idx ON note (project_id);
