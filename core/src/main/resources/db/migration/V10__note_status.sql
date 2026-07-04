-- MFI note lifecycle (working states): STAGING = machine-drafted (capture/MCP),
-- waiting for the user's review; RESOURCE = reviewed, part of the trusted
-- knowledge base; ARCHIVED = discarded but kept ("stale"). Existing notes were
-- all user-curated, so they default to RESOURCE.
ALTER TABLE note ADD COLUMN status VARCHAR(16) NOT NULL DEFAULT 'RESOURCE';
ALTER TABLE note ADD CONSTRAINT note_status_check
    CHECK (status IN ('STAGING', 'RESOURCE', 'ARCHIVED'));

-- Tab lists and the staging badge filter by status.
CREATE INDEX note_status_idx ON note (status);
