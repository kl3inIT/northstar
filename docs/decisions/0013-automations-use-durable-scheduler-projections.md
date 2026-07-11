# 0013 - Automations Use Durable Scheduler Projections

## Status

Accepted.

## Context

Northstar already had process-local scheduled maintenance jobs, but Morning
Brief and later user workflows need schedules that can be changed at runtime,
survive restarts, avoid duplicate execution across worker replicas, retain run
history, and evolve without coupling every handler to scheduling infrastructure.
Spring `@Scheduled` cannot represent persisted per-user definitions. Quartz can
solve the problem but adds a larger operational and API surface than this
single-database application needs.

## Decision

- Use `automation_definition` as the product source of truth and
  `automation_run` as the product execution ledger.
- Use db-scheduler in `apps/worker` as the PostgreSQL-backed claiming, heartbeat,
  retry, and recurring-execution engine.
- Project definitions into db-scheduler through `SchedulerClient`; never make
  `scheduled_tasks` a product model or write it directly.
- Reconcile by monotonically increasing `scheduleVersion`, acknowledging only
  the exact version projected so concurrent edits are retried on the next pass.
- Keep user triggers typed as local time, weekdays, timezone, and catch-up
  window. Compile them to cron in the worker; do not expose raw cron in V1.
- Discover workflows through typed, versioned `AutomationHandler` beans. Keep
  credentials outside workflow JSON and resolve providers at execution time.
- Use deterministic business run/output keys in addition to scheduler claiming,
  because infrastructure acknowledgement and business effects cannot be one
  atomic transaction.

## Consequences

New workflow types register a handler and UI/config mapping without changing
scheduler code. Worker replicas share claiming and heartbeat state through the
existing PostgreSQL deployment, and Settings changes do not require restart.
The system owns a reconciliation loop and two related persistence models, so
projection lag must remain visible (`Syncing`) and monitored. Raw cron, complex
RRULEs, dependency graphs, per-step traces, notifications, and a full job
operations dashboard remain future extensions behind the same definition/run
contracts.
