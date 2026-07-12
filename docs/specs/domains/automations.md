# Automations Spec

## Current Behavior

Northstar has a durable automation foundation for user-configurable background
workflows. An automation definition stores a stable type, display name, enabled
state, typed trigger, versioned workflow configuration, schedule projection
version, and optimistic-lock version. Runs are separate durable records with
scheduled/manual origin, attempt count, status, timestamps, error details,
output reference, and metrics.

The current trigger contract supports daily or weekly schedules expressed as a
local time, selected weekdays, IANA timezone, and catch-up window. Raw cron is
not a V1 user input. The worker compiles the validated trigger into a
timezone-aware cron schedule, so a future advanced cron editor does not change
handler or run-history contracts.

The API owns automation CRUD and run-now requests. The worker owns execution:

- a cluster-safe `db-scheduler` reconciliation task projects changed
  definitions into `scheduled_tasks` every 10 seconds;
- one persistent dynamic task instance exists per automation;
- disabling/deleting a definition removes its projected schedule;
- a queued manual run is skipped if its definition is deleted before a worker
  claims it;
- missed executions run only inside the configured catch-up window;
- handler failures retry up to three times with exponential backoff;
- `(automation_id, scheduled_for)` is unique so a scheduled occurrence has one
  business run record even after scheduler redelivery.

`automation_definition` remains the product source of truth.
`scheduled_tasks` is an infrastructure projection and application code accesses
it only through `SchedulerClient`.

## Northstar Brief

`morning-brief.v1` remains the stable type id; its configuration schema is now
version 2. Runtime configuration contains language, lookback hours, maximum
items, topics, exact Firecrawl queries, blocked domains, output preference,
enabled source ids, GitHub repositories, RSS/Atom feeds, Bluesky handles, and a
Firecrawl credit budget. Credentials never enter workflow JSON.

Each run collects GitHub releases, RSS/Atom entries, Hacker News stories,
Bluesky author feeds, and bounded Firecrawl search results concurrently. Source
adapters fail independently, and successful sources still produce a brief. The
run fails only if every enabled source fails. Candidate URLs are canonicalized,
tracking parameters removed for identity, deduplicated, ranked by trust tier,
freshness, and community signal, then selected fairly across sources within
each trust tier before the item cap is applied. Persisted V1 configurations
that omit new V2 fields receive server defaults during deserialization.

Firecrawl is a discovery fallback rather than the default for every page. It
runs at most two requests concurrently, uses up to four searches and three
scraped Markdown results per query, selects the basic proxy, disables PDF
parsers, and refuses an estimated workflow budget outside 5–50 credits. Actual
usage is read from Firecrawl's `creditsUsed` response field. It does not use
Agent, Crawl, JSON extraction, enhanced proxy, or browser interaction. The
default 25-credit cap keeps one daily automation below 750 estimated credits
per 30-day month.

Default sources cover Codex, Claude Code, Flutter, Dart, Java, Spring AI, and
React. X and OpenClaw are not sources. Reddit scraping is not implemented;
future Reddit access requires its approved OAuth Data API.

The handler renders Markdown itself and optionally upserts a `STAGING` note in
`Briefs` titled `Morning Brief - <automation name> - <local date>`. Re-running
the same automation/day updates that note; distinct automation names do not
overwrite one another.

`/briefs` is a first-class newsroom with two intentionally separate tabs.
`HuggingNews` is a read-only live feed owned by the external provider;
`Northstar Brief` is the user's durable, scheduled brief. Both use a dense
editorial structure: filters, a TL;DR area, day-grouped ranked rows, and one
inline-expanded story at a time. Provider content is not copied into Northstar
notes merely because it was viewed.

The HuggingNews adapter reads only public SvelteKit page-data routes, normalizes
them behind `core.brief.BriefFeedProvider`, bounds response sizes, caches the
feed for five minutes and story details for thirty minutes, and serves the
last feed snapshot as explicitly stale when a refresh fails. It does not call
HuggingNews's internal Convex backend, reuse Clerk credentials, or persist
provider stories. The API exposes:

- `GET /api/briefs/huggingnews`;
- `GET /api/briefs/huggingnews/{topic}/{slug}`.

The Northstar tab keeps the issue selector, Staging status, parsed source
sections, inline story summaries, schedule/source configuration, `Run now`,
and the five most recent durable runs together. The existing worker handler,
retry policy, scheduler projection, and note output remain unchanged.

## Settings Contract

`Settings > Automations` lists generic jobs with active/paused and projection-sync
status, a human-readable local schedule, enable switch, run-now, edit, delete,
and the five most recent runs. Creation begins with a workflow-type catalog
loaded from `GET /api/automations/types`; choosing a supported type opens its
type-specific editor with defaults supplied by the handler descriptor. The
`morning-brief.v1` entry and its type are intentionally hidden from this generic
surface because Northstar Brief owns its configuration and history inside
`/briefs`. Its full-height Sheet still has Schedule, Content, and Sources tabs.
It configures trigger and workflow fields without exposing raw cron or secrets.
Each source is enabled with a Switch and reveals a full-width
repository/feed/person editor; the Firecrawl key and optional GitHub token stay
in worker environment configuration. The layout is responsive and the Sheet
body scrolls independently while its actions remain available.

The backend handler registry is runtime-discovered. The web keeps a small
editor registry keyed by the same stable type id because typed workflow forms
need domain-specific controls and validation; adding a backend type makes it
visible in the catalog, while enabling creation requires its matching editor.

Assistant and MCP expose the same automation tool beans. Generic tools list
types, definitions, and runs; queue a manual run; pause/enable; and delete with
the current optimistic version. Creation and full editing are type-specific:
`save_morning_brief_automation` has a strict schedule/research schema instead of
accepting an untyped workflow-config map. Future workflow types add their own
typed save tool while reusing the generic read/control tools.

REST endpoints are:

- `GET/POST /api/automations`;
- `GET/PUT/DELETE /api/automations/{id}`;
- `GET /api/automations/types`;
- `POST /api/automations/{id}/runs`;
- `GET /api/automations/{id}/runs`.

## Source Modules

- `core.automation`
- `core.brief`
- `apps.api.automation`
- `apps.worker.automation`
- `apps.worker.brief`
- `integrations.web-openai`
- `integrations.news-huggingnews`
- `web/components/settings/automations-section`
- `web/pages/briefs`
