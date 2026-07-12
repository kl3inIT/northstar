# Northstar Roadmap

This file tracks delivery status. Increment descriptions and rationale live in
`docs/vision.md`; implementation details live in active increment plans.

## Status

| Increment | Status | Notes |
| --- | --- | --- |
| Repository and domain foundation | Done | Spring Modulith `core`, API/MCP/worker apps, Flyway schema, PostgreSQL/pgvector, web shell. |
| Knowledge base baseline | Done | Markdown notes, folders/tags, links/backlinks, note status, search surface, and optional primary-project linking with project note panels. |
| AI capture baseline | Done | Capture endpoints and AI-backed note drafting path. |
| Planning baseline | Done | Disciplines, projects/milestones, tasks, calendar events, recurrence, free-slot lookup. |
| Assistant and MCP tools | Done | In-app assistant tools, reactive Vercel streaming with structured sources, encrypted typed gateways with explicit OpenAI/9Router/chat-compatible capabilities, per-conversation searchable model selection, native AI Elements attachments/actions/citations, and streamable-http MCP exposure. |
| Web authentication baseline | Done | Single-user Spring Security session login, SPA CSRF, auth guard, logout. |
| Durable web sessions | Done | 30-day persistent cookie and PostgreSQL-backed Spring Session survive browser/API restarts. |
| Finance tracking V1.5 | Done | Capture-first VND ledger, explicit bank/cash/e-wallet/other balance reconciliation with latest-only undo, learned category corrections, budgets, savings goals, subscription auto-post/detection, CSV, Insights, receipt/SMS extraction, assistant/MCP tools, and weekly review facts. |
| Web research V1 | Done | Provider-neutral runtime routing, shared-gateway OpenAI and 9Router search, 9Router fetch combos, safe direct page reading, Assistant-only tools, citations, and Settings routes without duplicate credentials. YouTube/PDF/browser readers remain deferred. |
| Briefs newsroom and Northstar Brief V2 | Done | Separate HuggingNews live and app-owned Northstar tabs share a dense editorial reading model without mixing storage or ownership. Northstar retains persisted typed schedules, run history/retries, free GitHub/RSS/Hacker News/Bluesky discovery, budgeted Firecrawl enrichment, and sourced Staging notes; its settings now live beside its issues. Raw cron, Reddit OAuth, and extra delivery channels remain deferred. |
| Environment configuration profiles | Done | Safe common defaults plus explicit local/prod profiles for API, MCP, and worker; process-budgeted Hikari pools, ECS production logs, restricted Actuator exposure, secure proxy/cookie policy, and coordinated graceful shutdown. |
| Mobile app foundation | Done | Flutter 3.44/Dart 3.12 scaffold with Android, iOS, and Web targets plus Cupertino-first platform foundations. |
| Cupertino mobile shell | Done | Semantic design tokens, Cupertino-only app root, five compact tabs, expanded sidebar, Assistant landing, widget previews, compact/expanded tests, and real Web render validation. |
| Mobile token authentication and routing | Done | Separate bearer/refresh protocol, hashed rotating refresh families, replay revocation, Keychain/Android secure storage, Cupertino login, and guarded `go_router` branches. Native device accessibility validation remains. |
| Mobile CI | Done | Path-scoped Linux quality/Web/Android gate and macOS unsigned iOS build pass on GitHub-hosted runners and publish seven-day review artifacts. |
| iOS Sideload IPA and mobile Assistant | Done | CI packages a checksum-verified unsigned IPA for Sideloadly; Flutter provides authenticated SSE chat with Cupertino builders, history, waiting, partial text, tool progress, stop, failure, and retry. Native install validation remains. |
| Reviewed Mobile Capture | Done | Focused Cupertino flow for text and receipt-image drafts, editable review, explicit note/task/event/expense writes, batch-safe undo, shared token refresh, compact/dark tests, and a real local API walkthrough. Voice capture and native-device validation remain. |
| Study tutor V1 | Done | Capture-first study log with weekly summary and mock trend, FSRS-6 vocabulary scheduling, writing tutor with sourced anchored grading + evaluator loop + error corpus, `/study` page, assistant/MCP tools, weekly review facts. Brief section and bulk imports remain deferred. |
| Vocabulary review V1 | Done | Focused keyboard-first due-card sessions on `/study`, independent English/Chinese libraries, flat General/IELTS/HSK-style deck scopes, default IPA/pinyin and part of speech, advisory answer checks, learner-owned FSRS ratings with real interval previews, pronunciation, and explicit preview/apply AI enrichment. Chat remains an optional due-card quiz path. |
| Vocabulary production and rich enrichment | Done | Optional independent meaning→target FSRS schedule with deck defaults and per-card overrides, next-day sibling burying, leech detection, best-effort word formation, provider-routed mnemonic images, and in-API background preview/apply jobs that never persist discarded image data. |
| Speech assessment V1 | Done | Provider-neutral speech port with Azure Speech SDK delivery measurement, live card pronunciation, one-question Speaking practice, routed AI content coaching, shared grammar corpus, and four-tab Study UI. The UI keeps provider 0-100 measurements separate from a rubric-grounded, LOW-confidence unofficial one-answer IELTS-style range with criterion evidence and scorer version; no provider score is converted into a band. Live provider/grader and browser flows are verified. |
| AI capability catalogs and on-demand text to speech | Done | Explicit Assistant read-aloud, isolated Chat/TTS/STT/Image/Embedding route catalogs, OpenAI/9Router discovery with manual fallback, TTS language/voice selection, persisted content-addressed MP3 reuse, and responsive AI Elements selectors/playback. No automatic generation or playback. |
| Today dashboard | Deferred | Assistant/chat is the daily cockpit for composing tasks, calendar, projects, finance, and review context. Revisit only if a zero-prompt glance surface becomes necessary. |
| Repository documentation harness | Done | Thin agent map, architecture source of truth, domain specs/tests, decisions, increment history, and testing guidance are consolidated and maintained with code changes. |

## Backlog

- Study V1.5: brief study section (after automation/brief), scored Task 1
  anchors, LanguageTool sidecar, bulk HSK/Tatoeba imports, embedding-based
  new-card dispersion.
- Shadowing and dictation: reuse persisted speech assets for explicit per-card
  generation, at-risk vocabulary sentences, typed recall, and diff grading.
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
