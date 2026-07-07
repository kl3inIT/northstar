# Northstar Vision

Northstar is a personal growth operating system for one primary user, with room
for an optional partner/shared workspace later.

## Problem Thesis

The problem is not that people need another todo list, template, vault, or
dashboard. Many productivity tools create a second job: managing the system
itself.

Every capture asks for a decision: task, note, project, habit, resource,
journal, folder, tag, deadline, view. That overhead makes organizing life feel
like additional work.

Northstar starts from a different question: not only "what do I need to do
today?", but "what kind of life am I trying to move toward?"

## Product Intent

Northstar combines:

- Obsidian-lite Markdown knowledge base.
- AI capture inbox for turning raw thoughts into structured notes and entities.
- Projects, tasks, disciplines, calendar, and daily planning.
- Study support for IELTS/HSK and scholarship applications.
- Finance, habit, and life tracking as future personal OS modules.
- MCP access so coding agents and other assistants can read and write explicit
  project-local context instead of relying on chat memory.

The name Northstar represents a guiding direction for personal development.

## Product Thesis

Northstar organizes work by meaning before mechanics:

- Life creates meaning.
- Disciplines create direction.
- Projects create action.
- Tasks and calendar blocks make the next step concrete.
- Notes preserve durable context and learning.

The product should stay clear and small, focus on what is relevant and
important, do just enough for now, and polish later.

## Methodology

The planning spine follows Life -> Disciplines -> Projects:

- A discipline is a durable area of responsibility or growth, such as IELTS,
  career, health, finance, or a scholarship track.
- A project is a larger outcome inside a discipline, such as "Chevening 2027" or
  "IELTS Band 7.0".
- Tasks, calendar blocks, notes, study logs, and future habits attach to that
  spine where useful.

## Current MVP Shape

The implemented baseline already centers on notes, capture, tasks, projects,
disciplines, calendar, assistant tools, MCP tools, attachments, and search.

The immediate product direction is to make daily use feel natural:

- Capture one natural sentence or audio note.
- Preserve the raw capture.
- Let AI draft notes or structured entities.
- Review and accept the result.
- Use Today, tasks, projects, and calendar to choose the next action.

## Build Philosophy

Northstar is being vibe-coded with a production harness rather than a
"prompt-and-pray" workflow.

The loop is:

1. Describe the intent.
2. Let AI accelerate implementation.
3. Compile and typecheck.
4. Run module and context tests.
5. Verify real flows when UI or runtime behavior matters.
6. Consolidate durable knowledge back into the repo docs.

Java and Spring Boot are intentionally part of the loop. The type system,
compiler, Flyway schema validation, Modulith boundaries, OpenAPI contract, and
tests make the feedback stricter than a quick UI demo. That friction is useful
because Northstar stores personal data, tasks, decisions, and agent-readable
context.

## Future Modules

Future modules tracked in the roadmap include:

- Today dashboard.
- Study tutor and study logs for IELTS/HSK.
- Scholarship/university research.
- Finance management.
- Habit tracking.
- Couple/shared workspace.
- Mobile app.
- Richer agent session continuity through MCP capture.

Architecture facts move to `ARCHITECTURE.md` only after the code actually lands.
