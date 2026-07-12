# Note Project Linking Design

## Problem

Projects are the execution layer, but notes currently only live in folders,
tags, and wiki links. A project can have tasks, milestones, and notes in the
user's workflow, but the app cannot show the notes that belong to one project.

## Scope

- Add one optional primary `projectId` to a note.
- Keep folders, tags, wiki links, backlinks, and search behavior unchanged.
- Validate a note's `projectId` against the project module public API when
  creating or updating a note.
- Add a project-note list API for project detail views.
- Let the note editor attach, move, or detach a note from a project.
- Show the linked project in note metadata and linked notes on project detail.

## Non-Goals

- No many-to-many note/project model yet.
- No automatic classification of existing notes.
- No folder-to-project migration.
- No project deletion blocking on notes; deleting a project detaches notes.

## Model

```text
Discipline -> Project -> Task
                    \-> Note
Note -> wiki links/backlinks -> many related notes
```

A note has zero or one primary project. Wiki links and backlinks remain the
many-to-many knowledge graph.
