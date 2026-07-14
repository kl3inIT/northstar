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
| Mobile IA V2 daily client | Done | `Today \| Study \| Assistant \| Finance \| More` keeps Assistant central while adding task/habit actions, Calendar, FSRS review, Finance glance, focused note retrieval, secondary Account/Settings routes, content-free telemetry, real local API/browser verification, and an Orca-hosted Pixel smoke. Native iPhone accessibility/install validation remains. |
| Study tutor V1 | Done | Capture-first study log with weekly summary and mock trend, FSRS-6 vocabulary scheduling, writing tutor with sourced anchored grading + evaluator loop + error corpus, `/study` page, assistant/MCP tools, weekly review facts. Brief section and bulk imports remain deferred. |
| Vocabulary review V1 | Done | Focused keyboard-first due-card sessions on `/study`, independent English/Chinese libraries, flat General/IELTS/HSK-style deck scopes, default IPA/pinyin and part of speech, advisory answer checks, learner-owned FSRS ratings with real interval previews, pronunciation, and explicit preview/apply AI enrichment. Chat remains an optional due-card quiz path. |
| Vocabulary production and rich enrichment | Done | Optional independent meaning→target FSRS schedule with deck defaults and per-card overrides, next-day sibling burying, leech detection, best-effort word formation, provider-routed mnemonic images, and in-API background preview/apply jobs that never persist discarded image data. |
| Speech assessment V1 | Done | Provider-neutral speech port with Azure Speech SDK delivery measurement, live card pronunciation, one-question Speaking practice, routed AI content coaching, shared grammar corpus, and four-tab Study UI. The UI keeps provider 0-100 measurements separate from a rubric-grounded, LOW-confidence unofficial one-answer IELTS-style range with criterion evidence and scorer version; no provider score is converted into a band. Live provider/grader and browser flows are verified. |
| AI capability catalogs and on-demand text to speech | Done | Explicit Assistant read-aloud, isolated Chat/TTS/STT/Image/Embedding route catalogs, OpenAI/9Router discovery with manual fallback, TTS language/voice selection, persisted content-addressed MP3 reuse, and responsive AI Elements selectors/playback. No automatic generation or playback. |
| Vocabulary audio practice | Done | Explicit target-language word/example TTS enrichment, applied-audio-first browser fallback, retained pronunciation recordings/history, provider-aware delivery trends, connected-speech live-following Shadowing, and deterministic Dictation are complete. Provider-native scores remain separate from FSRS, do not claim TTS synchronization, and are never presented as IELTS. |
| Habit tracking V1 | Done | Repeated-behaviour definitions stay separate from Tasks, with selected-day or weekly-target schedules, local-date check-ins, neutral excuse/pause semantics, effective-dated history, consistency-first insights, responsive web workspace, Assistant/MCP tools, and weekly review facts. |
| AI cache foundation | Done | Named exact caches now use Spring Cache with bounded/stat-tracked Caffeine defaults and a replaceable `CacheManager`; web, model-catalog, and HuggingNews-detail caches are migrated. Semantic response caching has a separate fail-closed, disabled-by-default port and never reuses Assistant/tool/grading or knowledge-vector data. |
| Temporary artifact lifecycle | Done | Provider-neutral owner/session/category-scoped artifacts use a bounded, expiring Caffeine default in the API. Vocabulary enrichment polls typed references instead of Base64, serves job-owned bytes with private headers, and cleans previews on discard/failure/expiry or only after a successful Apply commit. Durable attachments and future S3 storage remain separate. |
| Web Today dashboard | Deferred | Mobile now has a focused Today surface. Keep web Assistant/chat as the daily cockpit unless a separate desktop zero-prompt glance surface becomes necessary. |
| Repository documentation harness | Done | Thin agent map, architecture source of truth, domain specs/tests, decisions, increment history, and testing guidance are consolidated and maintained with code changes. |

## Backlog

- First semantic-cache workload/provider: only after a concrete stateless,
  read-only use case exists; use isolated storage (not the knowledge vector
  table), preserve owner/context/model/instruction scope, and add hit/miss plus
  sampled false-hit evaluation before enabling it.
- Agent runtime hardening, adopting patterns selectively from Spring AI
  AgentCore rather than taking its AWS runtime contract wholesale:
  conversation-summary consolidation outside the model window, async sampled
  assistant evaluations with Micrometer, and turn/session OpenTelemetry traces.
  Interactive browser and code sandbox capabilities remain a later increment.
- Study V1.5: brief study section (after automation/brief), scored Task 1
  anchors, LanguageTool sidecar, bulk HSK/Tatoeba imports, embedding-based
  new-card dispersion.
- Speaking debrief capture: practice conversation in ChatGPT voice, paste
  the takeaways back — capture/assistant classify into a Speaking session +
  errors into the grammar corpus.
- LLM reranker as a `DocumentPostProcessor` for knowledge search (Spring AI
  core ships the hook but no implementation; issue #5903).
- Scholarship/university research workflows.
- Couple/shared workspace with privacy.
- Native iPhone installation, VoiceOver, production API login, and remaining
  focused mobile product flows.
- Stronger automated/live UI coverage matrix.
- More complete per-domain specs and tests as future increments touch them.
