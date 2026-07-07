# 0001 - Repository As System Of Record

Status: accepted

## Context

Northstar work spans multiple sessions, agents, and tools. Keeping product
intent, architecture, plans, and decisions mainly in chat or in one large agent
prompt makes future sessions reread too much and re-derive prior choices.

## Decision

Use the repository as the system of record:

- `CLAUDE.md` and `AGENTS.md` stay thin entry maps and workflow rules.
- `ARCHITECTURE.md` records current architecture facts.
- `docs/vision.md` records product intent and intended future scope.
- `docs/roadmap.md` records delivery status.
- `docs/specs/` records current per-domain behavior.
- `docs/tests/` records per-domain coverage and gaps.
- `docs/guidelines/` records reusable mechanics.
- `docs/decisions/` records rationale for significant choices.
- `docs/increments/` records active and completed increment design/plan pairs.

## Consequences

Agents should start from the map, follow only relevant links, and update durable
docs when durable behavior changes. Completed increment docs are history, not
current state.
