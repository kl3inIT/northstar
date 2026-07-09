# Assistant And MCP Spec

## Current Behavior

Northstar exposes the same domain through in-app assistant tools and MCP tools
where possible.

The in-app assistant streams AI SDK UI Message Stream frames to the web chat.
Conversation text is stored in Spring AI's `spring_ai_chat_memory` table, while
tool workflow parts are stored in `northstar_assistant_tool_trace` and replayed
from `/api/assistant/history` so completed workflow steps survive page reloads
and conversation switches.

Current MCP tool areas:

- Knowledge: `search_knowledge`, `get_note`, `create_note`, `append_to_note`,
  `update_note`.
- Tasks: `today_tasks`, `upcoming_tasks`, `find_tasks`, `create_task`,
  `update_task`, `set_task_done`, `delete_task`. Both `create_task` and
  `update_task` take an optional project to file the task under (create files it
  in one step; update moves it, mirroring the dedicated project-assignment path).
- Calendar: `upcoming_events`, `create_event`, `update_event`, `delete_event`,
  `cancel_occurrence`, `find_free_slots`.
- Projects: `list_projects`, `project_status`, `create_project`,
  `update_project`, `delete_project`, `manage_milestone`.
- Disciplines: `list_disciplines`, `create_discipline`.
- Review: `draft_review`.

Agents should follow the Northstar usage guideline for note authoring:
Markdown is the note source format, existing notes are searched before creating
new ones, project-specific notes are filed with `projectId`, and Mermaid is
preferred for flows, lifecycles, architecture, dependency graphs, and decisions.

The MCP app uses streamable HTTP at `/mcp` and reads the same PostgreSQL schema
as the API.

## Source Modules

- `core.assistant`
- `apps/mcp`
- `apps/api.assistant`
