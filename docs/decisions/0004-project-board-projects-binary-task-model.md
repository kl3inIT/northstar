# 0004 - Project Board Projects the Binary Task Model

Status: accepted

## Context

Opening a project needs a task board. The obvious pattern is a status kanban
(To-do / Doing / Done), but task status in Northstar is deliberately binary
OPEN/DONE — a friction rule that keeps "done?" a yes/no question. A three-column
status board would either require inventing an in-progress status (breaking the
rule) or leave two of three columns meaningless. A time-bucket board (Overdue /
Today / This week / …) already exists as the Tasks-page projection, so reusing
it inside a project would only duplicate that view.

## Decision

The project detail board has three columns — Backlog, Planned, Done — that are a
projection of the existing task model, not a new status axis:

- Backlog = OPEN with no planned "do" date.
- Planned = OPEN with a planned "do" date (`plannedDate`, the Tasks-page star).
- Done = DONE.

Dragging a card maps to existing fields only: to Planned sets `plannedDate`, to
Backlog clears it, to Done completes the task (and moving out of Done reopens).
Task status stays binary OPEN/DONE.

Creating a task from the board files it under the project in one call:
`TaskService.create` gained a `projectId` overload, `TaskRequest` carries
`projectId` on create, and the `create_task` assistant/MCP tool takes an
optional `projectId`. Moving a task between projects remains the dedicated
`PATCH /tasks/{id}/project` path; update does not change project membership.

## Consequences

The board never introduces a third task status, and it stays distinct from the
time-bucket Tasks board rather than duplicating it. New behavior is a thin read
over `(status, plannedDate, projectId)`, so the board and the create-with-project
flow reuse existing services. The `plannedDate` field now carries a second
meaning (board lane) in addition to the do-vs-due star; the two are the same
signal by design.
