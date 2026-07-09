# Assistant And MCP Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| API assistant stream, history, and persisted tool workflow replay | Automated | `apps/api/src/test/java/com/northstar/api/assistant/AssistantControllerIntegrationTests.java` |
| MCP handshake, tools/list, and tool calls | Automated | `apps/mcp/src/test/java/com/northstar/mcp/McpServerIntegrationTests.java` |
| Daily/weekly review quality | Automated baseline | `apps/api/src/test/java/com/northstar/api/alignment/AlignmentServiceIntegrationTests.java` covers alignment service behavior. |
| Real external MCP client session | Gap | Needs live verification when MCP wiring changes. |
