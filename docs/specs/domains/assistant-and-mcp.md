# Assistant And MCP Spec

## Current Behavior

Northstar exposes the same domain through in-app assistant tools and MCP tools
where possible.

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

The MCP app uses streamable HTTP at `/mcp` and reads the same PostgreSQL schema
as the API.

## Source Modules

- `core.assistant`
- `apps/mcp`
- `apps/api.assistant`
