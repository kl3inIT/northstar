# Assistant And MCP Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| API assistant stream, history, and persisted tool workflow replay | Automated | `apps/api/src/test/java/com/northstar/api/assistant/AssistantControllerIntegrationTests.java` |
| Assistant waiting state before visible output | Static | `pnpm -C web typecheck`; still needs a browser regression around submitted/streaming turns. |
| Completed tool workflow remains stable | Runtime | Browser regression on 2026-07-11 ran a real read-only tool turn and verified the workflow ended expanded while the same `[role=log]` node remained connected (`detached=false`, `sameLog=true`); the header still permits explicit user collapse. |
| MCP handshake, tools/list, and tool calls | Automated | `apps/mcp/src/test/java/com/northstar/mcp/McpServerIntegrationTests.java`; includes `create_note` with explicit `status=RESOURCE`. |
| Daily/weekly review quality | Automated baseline | `apps/api/src/test/java/com/northstar/api/alignment/AlignmentServiceIntegrationTests.java` covers alignment service behavior, including ordinary vs one-off finance facts. |
| Finance tool discovery and calls | Automated | `McpServerIntegrationTests` lists the budget, goal, and subscription tools and performs real write/read round trips, including create-time subscription `version`; `FinanceServiceIntegrationTests` covers list-derived version and expected-due safety. |
| API-only web tools | Automated + runtime | `WebResearchControllerIntegrationTests` confirms in-app discovery. The web tools carry no `@McpTool`; live Assistant search/read verification is recorded in `web-research.md`. |
| Real external MCP client session | Gap | Needs live verification when MCP wiring changes. |
