# Habits Spec

## Current Behavior

Northstar treats a habit as a repeated behaviour, not as a recurring Task.

- A habit owns a title, optional cue and notes, color, timezone, lifecycle, and
  an effective-dated schedule.
- `ON_DAYS` schedules choose ISO weekdays. Selecting all seven means daily.
- `WEEKLY_TARGET` schedules require any N distinct days in an ISO week (1-7).
- A local-date check-in records either `DONE` or `EXCUSED`. Writing the same
  date again replaces its state; clearing it restores the derived state.
- Check-ins cannot be recorded in the future, during a pause, or against an
  archived habit.
- Schedule edits become effective on a date and preserve prior interpretation.
- Pauses are dated intervals. Paused days are visible but excluded from
  expected opportunities. Resume closes the current pause.
- Archive hides a habit from active work but retains its definition, schedule,
  pause, and evidence history. Archived habits can be restored.

## Derived State

The server derives Today and Insights views from schedules and evidence.

- Day state is one of `DONE`, `EXCUSED`, `OPEN`, `MISSED`, `PAUSED`, or
  `NOT_SCHEDULED`.
- Today returns the last seven local dates, weekly progress, 30/90-day
  consistency, and current/best scheduled-day streaks.
- Consistency is completed / expected. Excused and paused dates are neutral and
  therefore removed from the denominator.
- Streaks are secondary derived information and are not persisted. Flexible
  weekly-target habits use weekly progress rather than a misleading daily
  streak.
- Insights accept a bounded 1-366-day range and return per-day evidence plus
  expected, completed, excused, consistency, and streak summaries.

## User Surfaces

- `/habits` exposes `Today`, `All habits`, and `Insights` tabs.
- Today provides one dominant check-in action, seven-day evidence, a quiet
  consistency summary, edit, excuse/clear, pause/resume, and archive actions.
- All habits separates active and archived definitions and supports restore.
- Insights uses the Kibo contribution graph for a scrollable 365-day rhythm and
  lists consistency by habit. Mobile opens the graph at the newest dates.
- Creation/editing supports cues, selected weekdays or weekly targets, color,
  and minimum-version notes in one responsive dialog.
- Assistant and MCP expose the same non-destructive workflow through
  `HabitService`. Weekly Alignment notes include descriptive habit facts only
  when habits exist; the writer must treat pauses/excuses as neutral and avoid
  shame or streak-preservation advice.

## Product Boundaries

- Habits never create Task rows and never become permanently done.
- Finite obligations and recurring reminders stay in Tasks or Calendar.
- Study logs, subscriptions, quantified sensor values, social challenges,
  notifications, and automatic AI coaching are outside Habit V1.

## Source Modules

- `core.habit`
- `core.assistant.HabitTools`
- `core.alignment`
- `apps/api.habit`
- `apps/mcp`
- `web/src/features/habits`

## Related Decisions

- [0033 - Habits record repeated behaviour, not recurring tasks](../../decisions/0033-habits-record-repeated-behaviour-not-recurring-tasks.md)
