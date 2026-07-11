# Northstar Roadmap

This file tracks delivery status. Increment descriptions and rationale live in
`docs/vision.md`; implementation details live in active increment plans.

## Status

| Increment | Status | Notes |
| --- | --- | --- |
| Repository and domain foundation | Done | Spring Modulith `core`, API/MCP/worker apps, Flyway schema, PostgreSQL/pgvector, web shell. |
| Knowledge base baseline | Done | Markdown notes, folders/tags, links/backlinks, note status, search surface. |
| AI capture baseline | Done | Capture endpoints and AI-backed note drafting path. |
| Planning baseline | Done | Disciplines, projects/milestones, tasks, calendar events, recurrence, free-slot lookup. |
| Assistant and MCP tools | Done | In-app assistant tool definitions and streamable-http MCP tool exposure. |
| Web authentication baseline | Done | Single-user Spring Security session login, SPA CSRF, auth guard, logout. |
| Finance tracking V1.5 | Done | Capture-first VND ledger, balance reconciliation, learned category corrections, budgets, savings goals, subscription auto-post/detection, CSV, Insights, receipt/SMS extraction, assistant/MCP tools, and weekly review facts. |
| Web research V1 | Done | Provider-neutral runtime routing, OpenAI web search, safe direct page reading, Assistant-only tools, citations, and a general Settings page. YouTube/PDF/browser readers remain deferred. |
| Automation foundation and Morning Brief V2 | Done | Persisted typed schedules, run history/retries, free GitHub/RSS/Hacker News/Bluesky discovery, budgeted Firecrawl enrichment, sourced Staging notes, Settings management, and a dedicated Briefs reader. Raw cron, Reddit OAuth, and extra delivery channels remain deferred. |
| Mobile app foundation | Done | Flutter 3.44/Dart 3.12 scaffold with Android, iOS, and Web targets plus Cupertino-first platform foundations. |
| Cupertino mobile shell | Done | Semantic design tokens, Cupertino-only app root, five compact tabs, expanded sidebar, Assistant landing, widget previews, compact/expanded tests, and real Web render validation. |
| Mobile token authentication and routing | Done | Separate bearer/refresh protocol, hashed rotating refresh families, replay revocation, Keychain/Android secure storage, Cupertino login, and guarded `go_router` branches. Native device accessibility validation remains. |
| Mobile CI | Done | Path-scoped Linux quality/Web/Android gate and macOS unsigned iOS build pass on GitHub-hosted runners and publish seven-day review artifacts. |
| Study tutor V1 | Done | Capture-first study log with weekly summary and mock trend, vocab SRS on Ebisu recall probability reviewed through chat, writing tutor with sourced anchored grading + evaluator loop + error corpus, `/study` page, assistant/MCP tools, weekly review facts. Brief section, speaking assessment, and bulk imports remain deferred. |
| Today dashboard | Deferred | Assistant/chat is the daily cockpit for composing tasks, calendar, projects, finance, and review context. Revisit only if a zero-prompt glance surface becomes necessary. |
| Repository documentation harness | In progress | Apply repo-as-system-of-record structure and consolidate existing guidance. |

## Backlog

- Study V1.5: brief study section (after automation/brief), scored Task 1
  anchors, LanguageTool sidecar, bulk HSK/Tatoeba imports, embedding-based
  new-card dispersion.
- LLM reranker as a `DocumentPostProcessor` for knowledge search (Spring AI
  core ships the hook but no implementation; issue #5903).
- Scholarship/university research workflows.
- Habit tracking and streaks.
- Couple/shared workspace with privacy.
- Mobile product API integration, Assistant streaming, and production flows.
- Stronger automated/live UI coverage matrix.
- More complete per-domain specs and tests as future increments touch them.
