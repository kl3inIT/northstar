# Assistant And MCP Spec

## Current Behavior

Northstar exposes the same domain through in-app assistant tools and MCP tools
where possible.

The in-app assistant streams AI SDK UI Message Stream frames to the web and
Flutter chats.
The API returns a reactive Spring MVC SSE stream rather than manually bridging
to `SseEmitter`. It emits the AI SDK v1 response headers, disables Nginx
buffering, sends comment heartbeats every 15 seconds while a model or tool is
quiet, and preserves message/step/text/tool/finish boundaries through `[DONE]`.
Tool failures end their tool row with `tool-output-error`; a server-side turn
timeout emits `abort` and `[DONE]` instead of leaving the client busy. Client
disconnect cancellation propagates upstream through the reactive subscription.
Conversation text is stored in Spring AI's `spring_ai_chat_memory` table, while
tool workflow parts are stored in `northstar_assistant_tool_trace` and replayed
from `/api/assistant/history` so completed workflow steps survive page reloads
and conversation switches.
While a user turn is submitted or streaming, the web chat renders an assistant
waiting message until the latest assistant message has visible text, an image,
or a tool workflow part. This keeps the first turn and pre-tool-call gap from
looking frozen.

Model-backed workloads resolve an `AiTask` route at call time. Each route is a
configured gateway id plus model id; application YAML supplies defaults and a
database override from Settings wins without restarting either API or worker.
Gateway instances declare `OPENAI`, `NINE_ROUTER`, or
`OPENAI_CHAT_COMPATIBLE`. OpenAI and 9Router reuse the shared chat transport but
advertise the additional capability protocols Northstar has implemented;
generic compatible endpoints remain a conservative chat-only contract.
Deployment gateways remain read-only defaults;
Settings can also create, edit, test, and delete runtime gateway instances from
OpenAI, 9Router, OpenRouter, LiteLLM, or Custom presets. Presets provide form
defaults and select the gateway contract. Runtime API keys
are encrypted with AES-256-GCM using a deployment key and are never returned to
clients. The route editor and web/Flutter Chat offer models discovered from the
selected gateway. Chat selection is persisted per conversation; a new
conversation inherits the most recently used Assistant selection before falling
back to the Assistant task default.

Web research references those same gateway instances instead of storing another
URL or API key. OpenAI search calls `/responses` with the `web_search` tool and a
selected model. 9Router search and page reading call `/search` and `/web/fetch`
with a selected provider alias or combo. Connection testing stays in AI
Settings; Web Research only validates that the referenced gateway supports the
capability and that a target is present. Direct page and Firecrawl adapters
remain available as non-gateway alternatives.

The web Assistant composes its transcript from AI Elements primitives. The
composer and transcript share native attachment tiles; the native Prompt Input
provides drag/drop, paste, file-picker, screenshot, submit, and stop behavior;
message text exposes a native copy action; Chat uses the searchable Model
Selector with model/provider marks in both its trigger and results. The compact
composer keeps attachment actions first under `+`, followed by the current
model selector. The dialog groups models from every configured chat-capable
gateway, and selecting a model persists both its gateway and model id for that
conversation; external Markdown citations use Inline Citation hover cards.
Web-tool results emit Vercel `source-url` parts, while knowledge-search note and
file hits emit `source-document` parts. Both render in the native Sources
disclosure and survive transcript rehydration. Tool workflow and Northstar's
external-link confirmation remain application wrappers because they encode
product behavior beyond a generic presentation component.

Completed Assistant responses expose an explicit read-aloud action. It sends
only the final visible message text, excluding tool rows, source metadata, and
Markdown syntax, to a separate text-to-speech route and renders the result with
the AI Elements audio player. Synthesis never starts automatically. Generated
MP3 audio is stored as an immutable attachment; the same normalized text,
gateway, target, locale, and format returns the persisted asset without
validating or spending against the provider again. The TTS route is independent
from chat model selection. OpenAI targets combine a speech model and voice,
while 9Router discovers its normalized targets from `/models/tts`. The speech
asset contract deliberately has no Assistant-message ownership so Study cards
and shadowing can reuse it later.

The Flutter client authenticates the same REST/SSE contract with a Bearer access
token. Its typed service parses known text, tool, error, finish, and done frames;
the repository maps those frames into provider-neutral domain events; and a
`Listenable` ViewModel owns conversation history, partial text, tool progress,
stop, failure, and retry state. The compact iPhone layout uses a Cupertino
composer and modal history, while widths of at least 840 logical pixels keep
conversation history beside the chat. A new turn inserts an empty streaming
assistant message immediately, so the waiting state appears before the first
text or tool frame. Partial output remains visible after stop or failure, and
unfinished tool rows become failed instead of spinning forever.

The mobile UI uses `flutter_chat_ui` and `flutter_chat_core` only as its
backend-agnostic message-list component. Northstar owns the Cupertino builders,
Markdown rendering, transport, authorization, and agent/tool behavior; model
provider keys and orchestration remain on the backend.

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
returned sources as Markdown links and structured `source-url` stream parts,
and treats fetched text as untrusted data. Knowledge search emits note and file
citations as structured `source-document` parts rather than pretending they are
external URLs.

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
- `apps/api.speech`
- `core.ai`
- `core.speech`
- `integrations/ai-openai-compatible`
