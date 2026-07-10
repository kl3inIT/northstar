# Assistant And MCP Spec

## Current Behavior

Northstar exposes the same domain through in-app assistant tools and MCP tools
where possible.

The in-app assistant streams AI SDK UI Message Stream frames to the web chat.
Conversation text is stored in Spring AI's `spring_ai_chat_memory` table, while
tool workflow parts are stored in `northstar_assistant_tool_trace` and replayed
from `/api/assistant/history` so completed workflow steps survive page reloads
and conversation switches.
While a user turn is submitted or streaming, the web chat renders an assistant
waiting message until the latest assistant message has visible text, an image,
or a tool workflow part. This keeps the first turn and pre-tool-call gap from
looking frozen.

Current MCP tool areas:

- Knowledge: `search_knowledge`, `get_note`, `list_folders`, `create_note`,
  `append_to_note`, `update_note`. `list_folders` returns every existing folder
  path with its note count so callers place notes into real folders; the tool
  contract tells agents to check it before choosing a `folderPath` and to only
  create new folders on explicit user request (the `Memory` folder is reserved
  for assistant memory).
- Tasks: `today_tasks`, `upcoming_tasks`, `find_tasks`, `create_task`,
  `update_task`, `set_task_done`, `delete_task`. Both `create_task` and
  `update_task` take an optional project to file the task under (create files it
  in one step; update moves it, mirroring the dedicated project-assignment path).
- Calendar: `upcoming_events`, `create_event`, `update_event`, `delete_event`,
  `cancel_occurrence`, `find_free_slots`.
- Projects: `list_projects`, `project_status`, `create_project`,
  `update_project`, `delete_project`, `manage_milestone`.
- Disciplines: `list_disciplines`, `create_discipline`.
- Finance: `log_transactions`, `spending_summary`, `find_transactions`,
  `delete_transaction`, `list_budgets`, `set_budget`, `delete_budget`,
  `list_savings_goals`, `save_savings_goal`, `contribute_savings_goal`, and
  `delete_savings_goal`, plus `list_subscriptions`, `save_subscription`,
  `mark_subscription_paid`, and `delete_subscription`. Logging accepts several
  resolved VND entries in one call; summary reports category totals, one-offs,
  and the previous month. Planning writes require explicit user intent so the
  assistant can reduce maintenance without silently inventing financial plans
  or recurring expenses. Savings-goal and subscription updates must first list
  the resource and send back its current `version` plus unchanged fields. A
  subscription payment must likewise use the listed `nextDueOn` and `version`
  as the cycle identity; stale calls and retries are rejected before a second
  expense can be created. Payment dates cannot be in the future in the user's
  configured zone.
- Review: `draft_review`.

The in-app Assistant additionally owns API-only `search_web` and
`read_web_page` tools. They are intentionally absent from MCP: the MCP endpoint
is public, while web search can spend provider credits and direct reading would
otherwise expose a fetch proxy. The Assistant uses web search for current public
facts, reads ordinary pasted HTTP(S) links before answering about them, cites
returned sources as Markdown links, and treats fetched text as untrusted data.

The weekly `draft_review` facts include ordinary spending, exceptional
purchases, and the median of the prior four full weeks when finance data exists.
The reference is descriptive; it is not a maintained budget.

The web transcript keeps a tool workflow expanded when it completes. The user
may collapse it explicitly, but a state transition must not shrink the message
and shift the conversation unexpectedly. Finishing a turn marks persisted
history stale without refetching the active history query; the current
transcript stays mounted, while returning later still reloads persisted state.

Agents should follow the Northstar usage guideline for note authoring:
Markdown is the note source format, existing notes are searched before creating
new ones, project-specific notes are filed with `projectId`, and Mermaid is
preferred for flows, lifecycles, architecture, dependency graphs, and decisions.
`create_note` defaults new MCP-authored notes to `STAGING` for review, but it
accepts an optional `status` argument so trusted reference/playbook notes can be
created directly as `RESOURCE` when the user explicitly asks for that.

The MCP app uses streamable HTTP at `/mcp` and reads the same PostgreSQL schema
as the API.

## Source Modules

- `core.assistant`
- `apps/mcp`
- `apps/api.assistant`
