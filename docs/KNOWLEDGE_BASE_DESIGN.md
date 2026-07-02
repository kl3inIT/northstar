# Knowledge Base Design

Northstar should work like a small personal Obsidian, but with structured data and AI extraction.

## Goals

- Store personal knowledge, project notes, study notes, work sessions, decisions, and learnings.
- Let notes link to each other with wiki links.
- Support backlinks, project views, tags, daily notes, graph view, and AI search.
- Keep data portable through Markdown export.

## Storage Model

PostgreSQL is the source of truth. Markdown is the note body format.

Core tables:

```text
notes
note_links
note_aliases
tags
note_tags
projects
attachments
note_chunks
```

The raw note is always preserved:

```text
notes.content_markdown
```

AI can extract structured entities:

```text
tasks
decisions
learnings
expenses
study_logs
habit_logs
project_memories
scholarship_items
```

## Wiki Links

Use Obsidian-style links in Markdown:

```md
I fixed [[dth-crm Gradle recovery]] today.
This relates to [[Jmix stale generated output]].
```

Supported syntax:

```text
[[Note Title]]
[[Note Title|Display Text]]
[[Project/Note Title]]
```

On save:

1. Parse `content_markdown`.
2. Extract wiki links.
3. Resolve each link to a note by slug, title, or alias.
4. Create missing unresolved link records.
5. Update `note_links`.

Backlinks are just reverse queries:

```sql
select source_note_id
from note_links
where target_note_id = :currentNoteId;
```

## Search

Search should be hybrid:

```text
1. Keyword search
2. Full-text search
3. Tag/project/date filters
4. Semantic vector search
5. Recency and importance ranking
```

Recommended PostgreSQL features:

```text
tsvector for full-text search
pg_trgm for fuzzy title search
pgvector for semantic search
```

Search examples:

```text
"gradle jmix lỗi build"
"IELTS map question"
"học bổng AI Trung Quốc deadline"
"những lỗi tôi hay gặp khi làm dth-crm"
```

The app should return:

```text
matched notes
related notes
linked projects
extracted decisions/learnings/tasks
AI summary with citations back to notes
```

## Graph View

Graph view can be generated from `note_links`:

```text
node = note/project/tag
edge = wiki link, backlink, extracted relation, same tag
```

Do not build graph view first. It is useful after the note/link/search core works.

## Export

Export should produce an Obsidian-compatible folder:

```text
exports/
├─ daily/
├─ projects/
├─ study/
├─ finance/
└─ knowledge/
```

Each file should include frontmatter:

```md
---
id: note_id
project: dth-crm
source: mcp
tags: [gradle, jmix]
createdAt: 2026-06-28
---

# Note title

Markdown content with [[links]].
```

## First Implementation Slice

Build this first:

```text
Create/edit note
Parse wiki links
Show backlinks
Keyword search
Project filter
AI capture creates note
```

Then add:

```text
semantic search
graph view
Markdown export
MCP capture
AI question answering over notes
```
