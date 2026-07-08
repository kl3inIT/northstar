# 0005 - Notes Have One Primary Project

Status: accepted

## Context

Projects are Northstar's execution layer, and real projects accumulate durable
context: briefs, decisions, research, drafts, and retrospectives. Before this
decision, notes only had folders, tags, and wiki links. That made browsing and
search work, but project detail pages could not show the notes that belong to a
project.

A many-to-many project-note model would be more expressive, but it would also
make capture and editing heavier: every note would need relationship management
before the app has proven that complexity is necessary.

## Decision

A note has zero or one primary `projectId`.

The primary project answers "where does this note mainly belong?" Project pages
can list their linked notes, and the note editor can attach, move, or detach a
note from a project.

Wiki links and backlinks remain the many-to-many knowledge graph. Folders and
tags remain browsing aids, not the source of project membership.

## Consequences

Project context is explicit without turning every note into relationship
maintenance. A note can still refer to many projects or topics through wiki
links, but only one project owns its primary execution context. If a project is
deleted, linked notes are detached rather than deleted.
