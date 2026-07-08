# Planning Spec

## Current Behavior

Northstar's planning spine is Life -> Disciplines -> Projects.

- A discipline is a durable area of responsibility or growth.
- Disciplines can be renamed/recolored. They can be permanently deleted only
  when no projects, tasks, or calendar events are still linked to them.
- A project is a staged piece of work under a discipline.
- Project progress is derived from milestones.
- Tasks are small todo items with status, due date/time, an optional planned
  "do" date, optional project, and optional discipline. A task can be filed
  under a project at creation time or moved between projects later.
- Opening a project shows a task board with Backlog / Planned / Done columns.
  The columns are a projection of the binary task model, not a third status:
  a card is Backlog when open with no planned date, Planned when open with a
  planned "do" date, and Done when completed. Dragging a card sets or clears the
  planned date (or completes it) — task status stays OPEN/DONE.
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
- [0004 - Project Board Projects the Binary Task Model](../../decisions/0004-project-board-projects-binary-task-model.md)
