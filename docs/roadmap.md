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
| Automation foundation and Morning Brief V1 | Done | Persisted typed schedules, db-scheduler worker projection, handler registry, run history/retries, Settings management, and sourced Staging-note briefs. Raw cron, extra delivery channels, and X/RSS remain deferred. |
| Mobile app foundation | Done | Flutter 3.44/Dart 3.12 scaffold with Android, iOS, and Web targets plus tested Cupertino/Material design selection. API integration and token authentication remain future work. |
| Cupertino mobile shell | Done | Semantic design tokens, Cupertino-only app root, five compact tabs, expanded sidebar, Assistant landing, widget previews, compact/expanded tests, and real Web render validation. |
| Mobile token authentication and routing | Done | Separate bearer/refresh protocol, hashed rotating refresh families, replay revocation, Keychain/Android secure storage, Cupertino login, and guarded `go_router` branches. Native device accessibility validation remains. |
| Today dashboard | Deferred | Assistant/chat is the daily cockpit for composing tasks, calendar, projects, finance, and review context. Revisit only if a zero-prompt glance surface becomes necessary. |
| Repository documentation harness | In progress | Apply repo-as-system-of-record structure and consolidate existing guidance. |

## Backlog

- Study tutor and structured study logs for IELTS/HSK.
- Scholarship/university research workflows.
- Habit tracking and streaks.
- Couple/shared workspace with privacy.
- Mobile product API integration, Assistant streaming, and production flows.
- Stronger automated/live UI coverage matrix.
- More complete per-domain specs and tests as future increments touch them.
