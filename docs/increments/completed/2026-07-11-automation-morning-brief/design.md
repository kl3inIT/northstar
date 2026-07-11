# Automation Foundation And Morning Brief V1

## Problem

Northstar currently has two process-local `@Scheduled` sweeps with hard-coded
intervals. They are appropriate for internal maintenance, but the product has no
durable, runtime-configurable scheduling model for user automations. Morning
Brief is the first such workflow: its local schedule, topics, source queries,
selection limits, and output behavior must be editable without restarting the
worker.

## Scope

This increment adds:

- a provider-neutral automation domain with typed trigger and workflow config;
- persisted automation definitions and execution history;
- `db-scheduler` as the worker-only persistent execution engine;
- a cluster-safe reconciler that projects desired definitions into the
  scheduler table;
- a handler registry so later workflows do not change scheduler code;
- CRUD, run-now, history, and supported-type REST contracts;
- Settings > Automations management on desktop and mobile;
- Morning Brief V1 as the first real handler, using configured web search,
  deterministic rendering, idempotent daily output, and a Staging note.

X, RSS subscriptions, email/push delivery, multi-agent research, raw cron UI,
and a separate brief-reading dashboard are outside V1. Web search queries are
the initial external source; the brief source contract remains extensible.

## Architecture

`core.automation` owns definitions, trigger/config validation, execution
history, supported-type metadata, and the handler registry. Full workflow
configuration remains JSONB so each stable type key can evolve behind an
explicit config version; common scheduling fields remain typed columns/JSON.

`automation_definition` is the source of truth. `db-scheduler` owns only its
`scheduled_tasks` projection. Because API and worker are separate processes,
the worker runs a cluster-safe reconciliation task that schedules, reschedules,
or cancels dynamic instances when a definition's schedule version changes.
No application code writes the scheduler table directly.

The generic scheduled task stores only the scheduler `Schedule`, automation id,
and projected version. At execution it reloads the current definition and calls
the matching `AutomationHandler`. A unique `(automation_id, scheduled_for)` run
record makes business effects idempotent even if a process dies after producing
output but before the scheduler acknowledges completion.

Morning Brief lives in `core.brief`. It runs a bounded set of configured web
queries, normalizes and de-duplicates cited sources, applies deterministic caps,
and renders Markdown in code. The model/provider may compose a concise sourced
answer per query, but it does not render the final document. The daily note title
and automation/day run key are deterministic; reruns update/reuse the existing
output instead of creating duplicates.

## Scheduling Semantics

- User-facing triggers are DAILY or WEEKLY with local time, selected weekdays,
  timezone, and catch-up window. Raw cron is not exposed in V1.
- Times are resolved in the configured IANA timezone and persisted/executed as
  instants.
- A past-due execution runs after worker recovery only inside its catch-up
  window; otherwise it records SKIPPED and advances normally.
- One dynamic task instance exists per automation, so runs do not overlap.
- Failures retry three times with exponential backoff, then advance to the next
  normal occurrence and remain visible in Northstar run history.
- Disabling an automation prevents execution immediately at the domain guard;
  reconciliation subsequently removes its scheduler projection.

## Configuration And Secrets

Trigger configuration is separate from workflow configuration. Morning Brief
stores topics, queries, lookback, maximum items, language, and note delivery
preference. Credentials and provider API keys never enter automation JSON; the
handler uses the existing runtime-selected web provider and server-held secret.

Every config payload is validated before persistence. Stable type keys include
their contract generation (`morning-brief.v1`), and records carry a config
version so later migrations do not depend on Java serialization compatibility.

## Verification

- domain integration tests cover validation, optimistic updates, run
  idempotency, disable/delete behavior, and supported types;
- worker tests cover projection reconciliation, due execution, catch-up skip,
  retry behavior, and two scheduler instances against one PostgreSQL database;
- Morning Brief tests use a fake search provider and verify deterministic
  deduplication, source links, empty state, and one-note-per-day behavior;
- API tests cover CRUD, run-now, history, invalid configs, and no secret
  exposure;
- browser tests cover creating, editing, disabling, running, and inspecting an
  automation at desktop and mobile widths.
