# Planning Spec

## Current Behavior

Northstar's planning spine is Life -> Disciplines -> Projects.

- A discipline is a durable area of responsibility or growth.
- Disciplines can be renamed/recolored. They can be permanently deleted only
  when no projects, tasks, or calendar events are still linked to them.
- A project is a staged piece of work under a discipline.
- Project progress is derived from milestones.
- Tasks are small todo items with status, due date/time, optional project, and
  optional discipline.
- Calendar events represent scheduled time blocks and support recurrence.
- Free-slot lookup derives available time from calendar events.

## Usage Semantics

- Create disciplines before projects when a durable area exists.
- Create projects for multi-step outcomes inside a discipline.
- Create tasks for concrete next actions.
- Create calendar events when time is reserved, not merely when work exists.
- Create notes for durable knowledge, rationale, learning, and context.
- Before deleting a discipline, move or delete linked projects, tasks, and
  calendar events. Notes are not deleted by discipline removal; discipline-note
  membership is tag-derived.

## Source Modules

- `core.discipline`
- `core.project`
- `core.task`
- `core.calendar`
- `apps/api` planning controllers
- `apps/mcp` planning tools

## Related Decisions

- [0003 - One Domain, Three Deployables](../../decisions/0003-one-domain-three-deployables.md)
