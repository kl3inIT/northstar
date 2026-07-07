# Knowledge Base Spec

## Current Behavior

- Notes are stored in PostgreSQL with Markdown bodies.
- Markdown is the portable note representation.
- Wiki-style links such as `[[IELTS Speaking]]` are part of the note model.
- Link resolution and backlinks are derived from note content.
- Notes support folders, tags, and working status.
- Search uses durable note data plus derived keyword/vector search data.
- Attachments can be indexed into searchable text or captions where supported by
  the search/indexing pipeline.

## Source Modules

- `core.note`
- `core.search`
- `core.attachment`
- `apps/api` note, attachment, and search delivery
- `apps/mcp` note/search tools

## Related Decisions

- [0002 - Database-First Markdown Knowledge Base](../../decisions/0002-database-first-markdown-knowledge-base.md)
