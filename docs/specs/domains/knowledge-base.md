# Knowledge Base Spec

## Current Behavior

- Notes are stored in PostgreSQL with Markdown bodies.
- Markdown is the portable note representation.
- Wiki-style links such as `[[IELTS Speaking]]` are part of the note model.
- Link resolution and backlinks are derived from note content.
- The reading view renders fenced Mermaid blocks as diagrams, so notes can carry
  process flows, architecture maps, lifecycle diagrams, dependency graphs, and
  decision flows without a separate diagram field.
- Notes support folders, tags, and working status.
- Notes can be attached to zero or one primary project. This project link is the
  note's execution context; wiki links/backlinks remain the many-to-many
  knowledge graph.
- Search uses durable note data plus derived keyword/vector search data. The
  assistant-facing search path fuses lexical and semantic rankings with
  Reciprocal Rank Fusion; lexical note search uses a multilingual-friendly
  PostgreSQL `simple` tsvector plus title trigram fallback.
- Attachments can be indexed into searchable text or captions where supported by
  the search/indexing pipeline.
- The reading view suppresses a duplicate first `# Heading` when it exactly
  matches the note title, so imported Markdown can keep its own title while the
  app avoids rendering two identical H1s.

## Source Modules

- `core.note`
- `core.search`
- `core.attachment`
- `apps/api` note, attachment, and search delivery
- `apps/mcp` note/search tools

## Related Decisions

- [0002 - Database-First Markdown Knowledge Base](../../decisions/0002-database-first-markdown-knowledge-base.md)
- [0005 - Notes Have One Primary Project](../../decisions/0005-notes-have-one-primary-project.md)
- [0007 - Hybrid Search Uses Reciprocal Rank Fusion](../../decisions/0007-hybrid-search-uses-rrf.md)
