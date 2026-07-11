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

## Morning Brief V1

`morning-brief.v1` is the first registered automation handler. Its runtime
configuration contains language, lookback hours, maximum sources, topics,
exact queries, blocked domains, and whether to save the result as a note.
Credentials and provider selection never enter workflow JSON; each run uses the
effective provider from Web Research Settings.

A run executes at most six searches with at most three concurrent virtual
threads. Exact queries replace topic-generated queries. Individual query
failures do not discard successful sections, but the run fails if every search
fails. Sources are canonicalized, tracking parameters removed for identity,
deduplicated in deterministic order, and capped before rendering.

The handler renders Markdown itself and optionally upserts a `STAGING` note in
`Briefs` titled `Morning Brief - <automation name> - <local date>`. Re-running
the same automation/day updates that note; distinct automation names do not
overwrite one another.

## Settings Contract

`Settings > Automations` lists jobs with active/paused and projection-sync
status, a human-readable local schedule, enable switch, run-now, edit, delete,
and the five most recent runs. Creation begins with a workflow-type catalog
loaded from `GET /api/automations/types`; choosing a supported type opens its
type-specific editor with defaults supplied by the handler descriptor. The
Morning Brief editor configures all V1 trigger and workflow fields without
exposing raw cron or secrets. The layout is responsive and the editor scrolls
independently on mobile.

The backend handler registry is runtime-discovered. The web keeps a small
editor registry keyed by the same stable type id because typed workflow forms
need domain-specific controls and validation; adding a backend type makes it
visible in the catalog, while enabling creation requires its matching editor.

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
- `integrations.web-openai`
- `web/components/settings/automations-section`
