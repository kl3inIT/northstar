# Habit Tracking — Design

## Goal

Add a focused habit domain that records repeated behaviour without duplicating
Tasks. Northstar should make daily check-in nearly frictionless, preserve the
history behind schedule changes and pauses, and describe consistency without
turning one missed opportunity into a total reset.

## Product boundary

- A Task is a finite obligation with an `OPEN -> DONE` lifecycle.
- A Habit is a repeated behaviour definition. It never becomes permanently
  done; each local-date check-in is evidence of one repetition.
- Low-frequency obligations such as rent or a dentist visit remain recurring
  tasks. Study scheduling, subscriptions and calendar events remain owned by
  their existing domains.
- Habits never generate Task rows.

## Decisions

### D1 — Definition, schedule and evidence are separate

`Habit` owns identity, title, cue, notes, colour and lifecycle. `HabitSchedule`
defines when repetitions are expected. `HabitCheckIn` records `DONE` or
`EXCUSED` for a local date. A unique `(habit_id, local_date)` constraint makes
check-in idempotent while an explicit clear operation repairs mistakes.

Schedules support:

- `ON_DAYS`: selected ISO weekdays; daily is all seven days.
- `WEEKLY_TARGET`: any N distinct days in an ISO week, 1–7.

Schedule changes are effective-dated. Editing today's schedule updates the
current version; later edits close the old version and create a new one. Past
metrics therefore retain the rules that were true at the time.

### D2 — Pause is neutral and archive is terminal presentation state

A pause is an effective-dated interval, not a missed run. Paused dates are
excluded from expected opportunities. Resuming closes the open interval;
archiving hides the habit from active work but retains definitions, schedules
and check-ins. An archived habit can be restored.

### D3 — Timezone and local date are part of the contract

All Today operations use the browser or caller's IANA timezone. Check-ins use
an explicit `LocalDate`; UTC instants are audit data only. This prevents streaks
from moving when server timezone or daylight-saving offset changes.

### D4 — Consistency is primary; streak is secondary

The read model returns:

- due/today state and seven-day history;
- this-week progress for weekly targets;
- completion rate over expected opportunities in the last 30 and 90 days;
- current and best streak for scheduled-day habits;
- a 365-day contribution series.

`DONE` counts as completion. `EXCUSED` is visible but removed from the expected
denominator. Paused dates are neither expected nor missed. Streaks are derived
and never stored. Weekly-target habits expose weekly progress instead of a
misleading daily streak.

### D5 — One quiet work surface

`/habits` has `Today`, `All habits` and `Insights` views. Desktop uses a dense
row matrix; mobile keeps one dominant check action and horizontally scrollable
history. Creation/editing uses a dialog on desktop and responsive full-width
layout on small screens. Kibo's contribution-graph composition is used as the
reference for the heatmap; data and domain calculations stay server-side.

No nested dashboard cards, giant rings, guilt copy, confetti, automatic AI
coaching or notification orchestration are included.

### D6 — Assistant and reviews are first-class clients

Assistant and MCP tools can list, create, edit, check in, excuse, clear, pause,
resume and archive habits through `HabitService`. Weekly Alignment facts include
planned/completed/excused counts and the least-consistent active habits. The LLM
describes those facts but never invents or auto-completes a habit.

## API

- `GET /api/habits?includeArchived=false`
- `GET /api/habits/today`
- `GET /api/habits/insights?from=&to=`
- `POST /api/habits`
- `PUT /api/habits/{id}`
- `PUT /api/habits/{id}/check-ins/{date}`
- `DELETE /api/habits/{id}/check-ins/{date}`
- `POST /api/habits/{id}/pause`
- `POST /api/habits/{id}/resume`
- `PATCH /api/habits/{id}/archived`

Every timezone-sensitive endpoint accepts `X-Timezone` and defaults only when
the caller omits it.

## Verification

1. Core tests pin schedule due dates, effective schedule changes, pause
   exclusion, idempotent check-in, completion rates and streak derivation.
2. API integration tests cover CRUD, timezone Today, check-in undo, pause and
   archive.
3. Assistant tool tests cover the user-facing operations.
4. OpenAPI is regenerated from the running API before web code consumes it.
5. Web typecheck/build and browser walkthroughs cover desktop, compact mobile,
   dark mode, keyboard focus, empty/loading/error states and responsive heatmap.
6. Full Gradle tests, Modulith verification and production web build end green.

