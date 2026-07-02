# Northstar Product Plan (UI-first)

Status: draft accepted direction, 2026-07-02.
Approach: wireframe all core screens in Penpot first to visualize features, then build in vertical slices.

## 0. Decisions already locked

- PostgreSQL is the single source of truth. Note body is Markdown with `[[wiki links]]`.
- No graph DB. Backlinks/graph derived from `note_links`. Escape hatch later: Apache AGE.
- Hybrid search: `tsvector` + `pg_trgm` + `pgvector`. Vector index is derived/disposable, never the source of truth.
- Obsidian integration = one-way export mirror (`northstar-vault/` folder). No two-way sync in v1.
- Mobile v1 = Telegram capture bot, not a mobile app.
- MCP server is a first-class interface for coding agents (see `docs/SESSION_CONTINUITY.md`).
- Memory scaling pattern (from hermes-agent/OpenClaw analysis): hot layer stays fixed-size, everything growing lives behind search; chunk by Markdown heading; SHA-256 dedup; periodic LLM compaction of old daily notes into `project_memories`.
- UI is built in code with real shadcn/ui (React + Tailwind); Penpot is used only for lo-fi greyscale wireframes to lock layout/flow. No hi-fi mockups in Penpot.

## 0.1 Chat / "Ask Northstar" — decision

Add a chat surface, but as a grounded assistant, not a generic chatbot. Rules:
- **Grounded + cited**: every answer cites source notes as clickable `[[wiki links]]`. No citation = not shippable.
- **Action-capable**: chat can call the same capture/extraction pipeline (e.g. "tạo task nộp đơn 15/7" creates a real Task entity).
- **Context-aware**: chat inherits the current module's context (Study → tutor mode; Scholarships → knows the pipeline).
- **Capture vs Chat are distinct surfaces in v1** (do not merge yet):
  - Capture = write-only, fire-and-forget ("ăn sáng 30k").
  - Chat = read + act, conversational ("tháng này tiêu bao nhiêu cho ăn uống?").
  - Unified single-box intent routing is a later, harder problem — deferred.
- **Roadmap slot**: general "Ask Northstar" chat lands after Phase 6–7 (MCP + semantic search exist, so it is just a new UI over existing RAG). Study tutor chat may come earlier (Phase 4) since it only needs lesson context, not the whole KB.

## 0.15 Calendar + Planner agent — decision

- **Calendar module added** (#12). It is a *time view over existing data*, not a silo: tasks with due dates, scholarship deadlines, study sessions, and habits all surface on it automatically. Views: Day / Week / Month. Right panel = today's time-blocked agenda ("what am I doing today"). Users drag captured tasks into time blocks; deadlines auto-appear. Add "Calendar" to the sidebar nav.
- **Planner agent = the assistant operating on time, not a 3rd AI surface.** It proposes a daily/weekly plan; it never silently reschedules. Rules:
  - **Suggest, don't auto-schedule.** Output is a *proposed* agenda the user accepts/tweaks. Trust dies the moment it moves things on its own.
  - **Grounded in real constraints:** hard deadlines (scholarships), fixed events, study targets (behind-target skill gets more blocks — ties into the requirement-gap engine), declared habits, and rough energy windows.
  - **Overload guard:** warns when a day is over-committed instead of cramming.
  - **Surface:** a "Plan my day / week" action that writes a proposed plan into the Calendar right panel; accept = it becomes real events via the same task/event pipeline.
  - **Roadmap slot:** after Calendar + enough captured tasks/deadlines exist (≈ Phase 5–6). v1 is a one-shot proposer, not an autonomous scheduler.

## 0.16 Methodology layer — Life → Disciplines → Projects (adapted from LDP)

Northstar's modules each borrow from a proven method (GTD capture, SM-2 SRS, goal-setting theory,
implementation intentions, deliberate practice). What ties them together is a spine adapted from
[life-disciplines-projects](https://github.com/uwidev/life-disciplines-projects):

```
life_goals    — why I live/strive ("du học TQ bằng học bổng, IELTS 7.0 by 03/2027")
disciplines   — areas I train continuously (Study-EN, Study-HSK, Finance, Health, Engineering)
projects      — finite endeavors under a discipline (hồ sơ Thanh Hoa, dth-crm)
tasks/habits/study_logs — actions, each FK-linked to a discipline (and optionally a project)
```

Schema additions: `life_goals` (title, target_date, success_criteria, note_id),
`disciplines` (name, life_goal_id, target/weekly-budget, note_id); add `discipline_id` FK to
tasks, habits, study_targets, projects. Every layer links to a note → the spine is browsable in the KB graph.

What this unlocks (consistency between modules):
- Requirement-gap = a life_goal's criteria projected onto a discipline's current progress.
- Today Dashboard reframes as "how did today move my goals", not just a task list.
- Planner agent allocates time blocks by discipline priority (behind-target gets more).
- Weekly Review has a fixed agenda: inbox zero → per-discipline progress → next week's blocks.

**Weekly Review (GTD ritual, AI-assisted):** a Sunday flow where AI pre-writes the review
(week summary from study_logs/tasks/expenses, per-discipline deltas, proposed adjustments) and the
user approves/edits. Saved as a weekly note. This is the mechanism that keeps the system alive.

Customization principle (LDP's own #1 rule): disciplines/goals are **data, not code** —
adding/renaming/reweighting disciplines is CRUD, so the system adapts per person (and per partner
in the couple workspace) without schema changes.

### Mechanisms adopted from the LDP vault itself (read from source, 2026-07-02)

1. **Alignment cadence** (LDP's review protocol — richer than a single weekly review):
   | Check | Question | Cadence | Northstar surface |
   |---|---|---|---|
   | Tasks → Projects | is today's work relevant to the project? | daily | Today Dashboard footer prompt |
   | Projects → Disciplines | is this project still serving the discipline? | weekly | Weekly Review |
   | Disciplines → Life | is this discipline still worth training? | quarterly | Quarterly review note |
   | Life → sense of self | is this still who I want to be? | yearly | Yearly review note |
2. **Ikigai as data**: LDP stores `ikigai: [love, world, money, skill]` in each discipline's YAML and
   *computes* Mission/Vocation/Profession/Passion + "potential Ikigai" (all 4) via dataview queries.
   Port 1:1: `disciplines.ikigai text[]` column + SQL views. The discipline with all 4 = your Ikigai →
   planner agent gives it priority weight.
3. **Guided setup wizard — NOT building for v1.** This app is single-user (the owner), so the owner
   defines `life_goals` + `disciplines` directly (DB seed or a plain Settings CRUD screen). The LDP
   interview flow (life pillars → 4-lens brainstorm → converge on Ikigai) is only worth building if
   Northstar is ever opened to other users. Until then: skip it, just make disciplines/goals editable.
4. **Friction rules** (enforced as soft app guardrails, not hard blocks):
   - Task ≤ 2h of work, phrased so "done?" is answerable yes/no; longer → split or promote to project.
   - **WIP limit: max 3 ongoing tasks** (Kanban). Capture is unlimited; *ongoing* is capped.
   - Project sized between 2h and ~1 month; single-digit total disciplines.
5. **True Deadlines**: only real-repercussion dates get deadline treatment (scholarship deadlines are
   true; self-imposed hustle dates are not). UI: true deadlines get the alarm styling + Telegram
   reminders; soft targets render as plain planned dates. Protects deadline credibility.
6. **Dashboards are queries, never hand-maintained** — LDP builds every dashboard from dataview/tasks
   queries over frontmatter. Northstar equivalent: all dashboards are SQL views over the spine; no
   module stores its own copy of state.
7. **Post-project ritual**: when a project ends, generalize reusable notes into the KB (Resources),
   archive the rest. Fits the compaction pipeline (§0 memory scaling) — a finished project triggers an
   AI-suggested "what's worth keeping" pass.

## 0.2 Wave 1 design-review findings (fix before Wave 2)

Senior-designer critique of the 5 lo-fi frames. Blocking items marked [B].
1. [B] Today Dashboard is empty below ~480px. Tighten height or add a "This week" strip (mini-calendar + streak heatmap). Empty space in a personal OS reads as a dead app.
2. [B] Capture review has too much friction: 12 decisions (3 buttons × 4 cards) for one sentence. Make "Confirm all" the 1-tap default; demote Reject to a small ✕ icon; Edit as ghost. Three equal buttons is wrong emphasis.
3. Confidence % is identical (92%) on every card = decoration. Make it behavioral: <70% auto-expands the card into edit mode / shows a warning border.
4. Note View right panel has 3 separate boxes (Backlinks/Outgoing/Metadata). Collapse metadata into a thin frontmatter line under the title; keep the right panel for backlinks only.
5. Wiki links must read as clickable in reading mode: drop the `[[ ]]` glyphs, use accent color + underline-on-hover. Raw `[[...]]` only appears in Source mode.
6. Search results are a flat matched-list. Per KNOWLEDGE_BASE_DESIGN.md, add an "AI answer" strip on top + related notes / linked projects sections.

## 1. Module inventory

| # | Module | Status of spec |
|---|--------|----------------|
| 1 | Today Dashboard | specced below |
| 2 | AI Capture Inbox | specced below |
| 3 | Notes / Knowledge Base | `docs/KNOWLEDGE_BASE_DESIGN.md` |
| 4 | Projects | thin v1: project = tag-like entity notes/tasks link to |
| 5 | Study: English (IELTS) + HSK | specced below (NEW) |
| 6 | Scholarships / university research | specced below (NEW) |
| 7 | Finance | thin v1: expenses from capture + monthly view |
| 8 | Habits | thin v1: habit list + daily check + streak |
| 9 | Tasks / life management | thin v1: task list + due dates, from capture |
| 10 | Couple / shared workspace | v1 = shared default workspace + Private visibility flag |
| 11 | Tools hub | backlog, not in v1 |
| 12 | Calendar / Planner | specced in §0.15; week/day/month views over existing data |
| 13 | Chat / "Ask Northstar" | specced in §0.1; post-Phase-6 |

### 1.1 Today Dashboard

One screen answering "what matters today":
- Today's tasks (due/overdue), habits to check, study target for today.
- Upcoming scholarship deadlines (next 14 days).
- Today's captured notes (daily note stream).
- Quick capture box (same parser as Capture Inbox).

### 1.2 AI Capture Inbox

- One text box. User writes one natural sentence (vi or en), or forwards via Telegram.
- AI parses into: note (raw text always preserved) + extracted entities (Task, Expense, StudyLog, HabitLog, Learning, Decision, ScholarshipItem update).
- Review UI: parsed entities shown as cards with confidence; user confirms/edits/rejects each. Confirmed entities are written; raw note is kept regardless.
- Inbox list = captures pending review.

### 1.3 Study module (English IELTS + HSK) — NEW SPEC

Goal: AI tutor + tracking that feeds the scholarship pipeline.

Data: `study_logs` (skill, minutes, source, date), `vocab_items` (term, lang, meaning, example, SRS state), `practice_submissions` (type, content, ai_feedback_note_id), `study_targets` (exam, target score, exam date).

Features:
- **Study dashboard**: streak, minutes per skill this week, target banner ("IELTS 7.0 by 2027-03 — writing is behind").
- **Vocabulary SRS**: words captured anywhere ("từ mới: resilient") land in one deck (EN + HSK hanzi). Daily review session, SM-2-style scheduling. Each vocab item is a note → linkable.
- **IELTS Writing practice**: paste/write essay → AI returns band estimate + criterion feedback (TA/CC/LR/GRA) → feedback saved as a note linked to `[[IELTS Writing]]` and to the essay. History view shows band trend over time.
- **IELTS Speaking practice** (phase 2 of module): record audio → transcript → AI feedback. Needs object storage; ship after writing.
- **Mistake bank**: recurring errors AI notices across submissions become notes tagged `#mistake`, resurfaced in future feedback (this is the memory loop applied to studying).
- **Study log**: every session logged, mostly auto-extracted from capture ("học 30p reading").
- **HSK**: same vocab SRS with hanzi fields (pinyin, tone), HSK-level tagging, level progress view.

### 1.4 Scholarship module — NEW SPEC

Goal: pipeline manager for hunting scholarships, connected to Study targets.

Data: `scholarship_items` (name, country, university, program, funding_pct, deadline, status, requirements jsonb, note_id), `application_documents` (checklist per item, file attachment refs).

Features:
- **Pipeline board (kanban)**: Discover → Researching → Preparing → Applied → Interview → Result. Card shows deadline countdown + requirement-gap badge.
- **Scholarship detail**: all fields + requirements checklist (e.g. IELTS 6.5, HSK 5, GPA) + documents checklist (CV, essay, transcript, LoR) + free-form research notes (Markdown, wiki-linked → shows in KB graph).
- **Requirement gap** (killer cross-module feature): requirement "IELTS 6.5" is compared against current Study target/progress → badge "gap: writing 5.5 → need 6.0". This is why Study and Scholarships live in one app.
- **Deadline timeline**: calendar/list of all deadlines; Telegram reminder at T-30/T-14/T-3 days.
- **Research capture**: pasting a scholarship URL into capture → AI extracts name/deadline/requirements into a Discover card.

### 1.5 Cross-module glue (what makes it one product, not 10 apps)

- Everything is a note or links to one → single KB graph, single search.
- Capture is the single write-path for humans; MCP is the single write-path for agents; both hit the same extraction pipeline.
- Today Dashboard is a read-only aggregation of all modules.

## 2. UI-first plan (Penpot)

Purpose: visualize every feature before writing code. Lo-fi wireframes only — validate layout, flows, and information architecture. No pixel polish, no real palette; move to code (React + shadcn/Tailwind) for high fidelity.

### 2.1 Setup

- One Penpot file `Northstar`, one page per module.
- Shared components first: **App shell** (sidebar: Today, Inbox, Notes, Study, Scholarships, Finance, Habits, Tasks, Settings), **note card**, **entity chip** (Task/Expense/StudyLog badge), **kanban card**.
- Desktop frames (1440) only for wave 1–2; one mobile frame later only for Telegram-bot message mockups.

### 2.2 Wireframe waves

**Wave 1 — core loop (do first, ~6 frames):**
| Frame | Question it must answer |
|---|---|
| App shell + Today Dashboard | is one screen enough for "what matters today"? |
| Capture Inbox — input state | how does 1 sentence → parsed entity cards look? |
| Capture Inbox — review state | how does confirm/edit/reject per entity work? |
| Note view (reading mode + backlinks panel) | Obsidian-like 3-zone layout: body / backlinks / metadata |
| Notes list + search results | snippet + filters (project, tag, date) |
| Note edit (source mode) | plain markdown editor, save flow |

**Wave 2 — study + scholarship (~6 frames):**
| Frame | Question it must answer |
|---|---|
| Study dashboard | streak + per-skill progress + target banner in one view |
| Vocab review session | one-card SRS flow (front/back/grade buttons) |
| Writing practice — submit + feedback | essay left, band + criterion feedback right, "saved as note" affordance |
| Scholarship kanban board | 6 columns readable? deadline + gap badge on card |
| Scholarship detail | requirements checklist + docs checklist + notes in one page |
| Deadline timeline | list vs calendar — pick one |

**Wave 3 — the rest (only after waves 1–2 validated):** Finance monthly view, Habit tracker, Task list, Settings/workspace (couple + privacy), MCP/session-log viewer.

### 2.3 Prototype links

Wire click-through for the core loop only: Today → capture → review → note created → note view → backlink → another note. If this loop feels good in Penpot, the product works.

### 2.4 Exit criteria for design phase

- Wave 1 + 2 frames done and click-through demoed.
- Each frame's "question" answered yes, or layout revised.
- Component inventory extracted (list of shadcn components needed) → feeds Phase 1 build.

## 3. Build order (after wireframes)

| Phase | Deliverable | Acceptance |
|---|---|---|
| 0 | Monorepo skeleton **(scaffolded 2026-07-02)**: Spring Boot 4.1 + Java 25 + Gradle KTS (version catalog + `build-logic` convention plugins); `core` library holds all Modulith modules; 3 apps `api`/`mcp`/`worker` (named `Northstar{Api,Mcp,Worker}Application`); Postgres+pgvector via `compose.yaml`; Flyway migrations in `core/src/main/resources/db/migration` (V1 = note, note_link, life_goal, discipline spine); web = Vite+React+shadcn (TanStack Router/Query); `.github` Dependabot+CI; `contracts/` OpenAPI. **Remaining for phase 0:** auth for 2 users, seed goals/disciplines, Today shell wired to real data. Spring AI deferred. | login → empty dashboard renders; seed goals/disciplines exist |
| 1 | Notes core: CRUD, wiki-link parse, `note_links`, backlinks, keyword search (tsvector) | create linked notes, see backlinks, search finds them |
| 2 | AI Capture: sentence → note + extracted entities + review UI | "ăn sáng 30k, học 30p IELTS" → Expense + StudyLog confirmed |
| 3 | Telegram bot: message → capture pipeline; deadline reminders out | capture from phone works end-to-end |
| 4 | Study v1: study logs, vocab SRS, writing feedback | daily vocab review + essay feedback saved as note |
| 5 | Scholarships v1: kanban, detail, deadlines, URL extract | full pipeline usable; reminder fires |
| 6 | MCP server: `northstar_capture`, `northstar_search_context`, `northstar_get_project_context`, `northstar_end_session_summary` | Claude Code captures a session and recalls it next session |
| 7 | Memory scale-up: pgvector semantic search, heading-based chunking + SHA-256 dedup, compaction job (old daily notes → summaries) | hybrid search returns relevant old context cheaply |
| 8 | Obsidian export mirror + graph view + requirement-gap engine | vault opens in Obsidian; gap badges live |
| 9 | Finance/Habits/Tasks views (data already exists from capture since phase 2) | monthly spend view, streaks, task list |
| 10 | Calendar + Planner agent (§0.15): week/day views, deadlines auto-surface, "Plan my day" proposer | drag task to time-block; proposed agenda accepted → real events |
| 11 | Weekly Review (§0.16, AI-prewritten) + "Ask Northstar" chat (§0.1, grounded + cited + action-capable) | Sunday review saved as weekly note; chat answers cite notes and can create entities |

Rationale for order: phases 1–3 create the daily-use habit (capture from anywhere); 4–5 deliver the two personal-goal modules; 6–7 make it AI-native; 8–9 are views over data already being collected.

## 4. Out of scope for v1

- Mobile app, two-way Obsidian sync, live-preview markdown editor, speaking audio grading, Tools hub, graph DB.
