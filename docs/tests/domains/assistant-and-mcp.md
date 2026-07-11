# Assistant And MCP Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| API assistant stream, history, and persisted tool workflow replay | Automated | `AssistantControllerIntegrationTests` verifies the real Spring MVC SSE response, Vercel headers/frames, memory, and replay; `UiMessageStreamTests` pins ordering, safe error, abort, `[DONE]`, and comment heartbeat behavior. Flutter `assistant_api_test.dart` verifies heartbeat comments are ignored and tool-error/abort frames parse. |
| AI route defaults, runtime overrides, and secret masking | Automated + runtime | `AiPropertiesTests` pins active-gateway defaults and secret-safe rendering; `AiRouteSettingsServiceTests` pins default/update/reset precedence. API and worker compile against the shared integration; local API boot verifies Flyway V32 and Settings/catalog endpoints. |
| Web and mobile model selection | Static + browser | Generated OpenAPI client plus web typecheck; Flutter analyze and Assistant service/ViewModel/widget suites; Playwright screenshots at 1440x1000 and 390x844 verify Settings and the Chat composer. |
| Assistant waiting state before visible output | Static | `pnpm -C web typecheck`; still needs a browser regression around submitted/streaming turns. |
| Completed tool workflow remains stable | Runtime | Browser regression on 2026-07-11 ran a real read-only tool turn and verified the workflow ended expanded while the same `[role=log]` node remained connected (`detached=false`, `sameLog=true`); the header still permits explicit user collapse. |
| MCP handshake, tools/list, and tool calls | Automated | `apps/mcp/src/test/java/com/northstar/mcp/McpServerIntegrationTests.java`; includes `create_note` with explicit `status=RESOURCE`. |
| Daily/weekly review quality | Automated baseline | `apps/api/src/test/java/com/northstar/api/alignment/AlignmentServiceIntegrationTests.java` covers alignment service behavior, including ordinary vs one-off finance facts. |
| Finance tool discovery and calls | Automated | `McpServerIntegrationTests` lists the budget, goal, and subscription tools and performs real write/read round trips, including create-time subscription `version`; `FinanceServiceIntegrationTests` covers list-derived version and expected-due safety. |
| API-only web tools | Automated + runtime | `WebResearchControllerIntegrationTests` confirms in-app discovery. The web tools carry no `@McpTool`; live Assistant search/read verification is recorded in `web-research.md`. |
| Real external MCP client session | Gap | Needs live verification when MCP wiring changes. |
