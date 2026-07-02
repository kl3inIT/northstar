# Session Continuity

Northstar should preserve project context across Claude Code, Codex, and other agent sessions.

## Current Safe Method

Use project-local files:

```text
AGENTS.md
PROJECT_CONTEXT.md
docs/*.md
```

Agents should read these files at the start of a new session.

## Why Not Edit Codex Internal State

Do not rely on editing `.codex` internal session files.

Reasons:

- Internal formats can change.
- Session state is not a stable public project API.
- Editing it can corrupt or confuse future sessions.
- It does not help Claude Code or other tools unless they share the same internal format.

The stable approach is explicit context:

```text
project files
MCP capture
Markdown export
database memory
```

## Future Native-Like Flow

Build a Northstar MCP server exposing tools:

```text
northstar_capture
northstar_save_learning
northstar_log_decision
northstar_create_task
northstar_search_context
northstar_get_project_context
northstar_end_session_summary
```

At the end of meaningful work, an agent should call:

```text
northstar_end_session_summary
```

The app stores:

```text
session summary
project touched
commands run
errors found
fixes applied
decisions made
follow-up tasks
learnings
```

Next session, the agent calls:

```text
northstar_get_project_context
```

This gives the same practical effect as session transfer, without depending on private Codex internals.

## Agent Instruction

Add this behavior to `AGENTS.md` once MCP exists:

```text
At the beginning of work, call `northstar_get_project_context`.
At the end of meaningful work, call `northstar_end_session_summary`.
Do not capture secrets, full env files, API keys, tokens, or raw logs unless explicitly requested.
Capture summaries by default.
```
