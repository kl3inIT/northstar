-- Notes get an Obsidian-style folder path plus free-form tags (Phase 1 IA).
--
-- folder_path is a slash-joined path like 'English/IELTS' ('' = root). The folder
-- tree in the UI is DERIVED from the distinct folder_path values, so folders need
-- no table of their own — a folder exists exactly while a note sits in it, just
-- like Obsidian where folders are implied by file paths.
ALTER TABLE note ADD COLUMN folder_path VARCHAR(1024) NOT NULL DEFAULT '';
CREATE INDEX idx_note_folder_path ON note (folder_path);

-- Tags as a normalized side table (indexable + joinable for tag filters/counts),
-- mapped by @ElementCollection on Note. Folders bucket, tags cut across, links graph.
CREATE TABLE note_tag (
    note_id UUID        NOT NULL REFERENCES note (id) ON DELETE CASCADE,
    tag     VARCHAR(64) NOT NULL,
    PRIMARY KEY (note_id, tag)
);
CREATE INDEX idx_note_tag_tag ON note_tag (tag);
