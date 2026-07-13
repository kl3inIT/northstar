# 0033 — Habits record repeated behaviour, not recurring tasks

Status: accepted

## Context

Northstar already owns finite work in Tasks and recurring obligations in Task
and Calendar recurrence. Treating a habit as a generated daily Task would
duplicate inbox state, create false overdue work and lose the distinction
between completing an obligation and strengthening a repeated behaviour.

Habit research also treats repetition in a stable context and increasing
automaticity as the defining mechanism. One missed opportunity should not erase
all prior progress.

## Decision

Habit is its own Modulith module. A definition owns effective-dated schedules;
local-date check-ins are separate evidence; pause intervals are neutral.
Schedules support selected weekdays and an ISO-week target. Completion rates
are primary, streaks are derived secondary data, and habits never generate Task
rows.

## Consequences

- Assistant, web, mobile and review generation share one HabitService contract.
- Schedule edits and pauses preserve historical interpretation.
- The service computes metrics from evidence instead of maintaining mutable
  counters.
- Notification delivery, quantified habits, sensors and social mechanics remain
  later capabilities rather than fields forced into V1.
