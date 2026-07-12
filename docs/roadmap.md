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
| Assistant and MCP tools | Done | In-app assistant tools, reactive Vercel streaming with structured sources, encrypted typed gateways with explicit OpenAI/9Router/chat-compatible capabilities, per-conversation searchable model selection, native AI Elements attachments/actions/citations, and streamable-http MCP exposure. |
| Web authentication baseline | Done | Single-user Spring Security session login, SPA CSRF, auth guard, logout. |
| Finance tracking V1.5 | Done | Capture-first VND ledger, balance reconciliation, learned category corrections, budgets, savings goals, subscription auto-post/detection, CSV, Insights, receipt/SMS extraction, assistant/MCP tools, and weekly review facts. |
| Web research V1 | Done | Provider-neutral runtime routing, shared-gateway OpenAI and 9Router search, 9Router fetch combos, safe direct page reading, Assistant-only tools, citations, and Settings routes without duplicate credentials. YouTube/PDF/browser readers remain deferred. |
| Automation foundation and Morning Brief V2 | Done | Persisted typed schedules, run history/retries, free GitHub/RSS/Hacker News/Bluesky discovery, budgeted Firecrawl enrichment, sourced Staging notes, Settings management, and a dedicated Briefs reader. Raw cron, Reddit OAuth, and extra delivery channels remain deferred. |
| Environment configuration profiles | Done | Safe common defaults plus explicit local/prod profiles for API, MCP, and worker; process-budgeted Hikari pools, ECS production logs, restricted Actuator exposure, secure proxy/cookie policy, and coordinated graceful shutdown. |
| Mobile app foundation | Done | Flutter 3.44/Dart 3.12 scaffold with Android, iOS, and Web targets plus Cupertino-first platform foundations. |
| Cupertino mobile shell | Done | Semantic design tokens, Cupertino-only app root, five compact tabs, expanded sidebar, Assistant landing, widget previews, compact/expanded tests, and real Web render validation. |
| Mobile token authentication and routing | Done | Separate bearer/refresh protocol, hashed rotating refresh families, replay revocation, Keychain/Android secure storage, Cupertino login, and guarded `go_router` branches. Native device accessibility validation remains. |
| Mobile CI | Done | Path-scoped Linux quality/Web/Android gate and macOS unsigned iOS build pass on GitHub-hosted runners and publish seven-day review artifacts. |
| iOS Sideload IPA and mobile Assistant | Done | CI packages a checksum-verified unsigned IPA for Sideloadly; Flutter provides authenticated SSE chat with Cupertino builders, history, waiting, partial text, tool progress, stop, failure, and retry. Native install validation remains. |
| Reviewed Mobile Capture | Done | Focused Cupertino flow for text and receipt-image drafts, editable review, explicit note/task/event/expense writes, batch-safe undo, shared token refresh, compact/dark tests, and a real local API walkthrough. Voice capture and native-device validation remain. |
| Study tutor V1 | Done | Capture-first study log with weekly summary and mock trend, vocab SRS on Ebisu recall probability reviewed through chat, writing tutor with sourced anchored grading + evaluator loop + error corpus, `/study` page, assistant/MCP tools, weekly review facts. Brief section and bulk imports remain deferred. |
| Speech assessment V1 | Done | Provider-neutral speech port with Azure Speech SDK delivery measurement, live card pronunciation, one-question Speaking practice, routed AI content coaching, persisted provider/model metadata, shared grammar corpus, four-tab Study UI, and dev-only live/provider plus mocked-browser verification. No IELTS band is inferred from provider scores. |
| Today dashboard | Deferred | Assistant/chat is the daily cockpit for composing tasks, calendar, projects, finance, and review context. Revisit only if a zero-prompt glance surface becomes necessary. |
| Repository documentation harness | In progress | Apply repo-as-system-of-record structure and consolidate existing guidance. |

## Backlog

- Study V1.5: brief study section (after automation/brief), scored Task 1
  anchors, LanguageTool sidecar, bulk HSK/Tatoeba imports, embedding-based
  new-card dispersion.
- TTS dictation (OpenAI `gpt-4o-mini-tts-2025-12-15` + audio player in chat):
  assistant reads sentences containing the user's at-risk vocab, user types
  what they hear, diff-graded — listening practice + vocab review in one.
- Speaking debrief capture: practice conversation in ChatGPT voice, paste
  the takeaways back — capture/assistant classify into a Speaking session +
  errors into the grammar corpus.
- Per-card pronunciation history (extends the speech-assessment increment's
  live-only card scoring with a persisted trend).
- LLM reranker as a `DocumentPostProcessor` for knowledge search (Spring AI
  core ships the hook but no implementation; issue #5903).
- Scholarship/university research workflows.
- Habit tracking and streaks.
- Couple/shared workspace with privacy.
- Remaining mobile product API integrations and production device flows.
- Stronger automated/live UI coverage matrix.
- More complete per-domain specs and tests as future increments touch them.
