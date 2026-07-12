# Finance Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| Monthly totals, net, category ordering, exceptional aggregate | Automated | `core/src/test/java/com/northstar/core/finance/FinanceAggregationTests.java` |
| Seed and used-category vocabulary merge | Automated | `FinanceAggregationTests` covers order, case-insensitive deduplication, and sorted additions. |
| V21-V44 schema plus record/edit/search/delete round trip | Automated | `apps/api/src/test/java/com/northstar/api/finance/FinanceServiceIntegrationTests.java` uses PostgreSQL Testcontainers and validates billing anchors, check-ins, and correction-memory migrations. |
| Previous-month and typical-week references | Automated | `FinanceServiceIntegrationTests` covers persisted summary math and the four-week median. |
| V22 monthly budget uniqueness and over-budget math | Automated | `FinanceServiceIntegrationTests` covers case-insensitive duplicate rejection, update/delete, and spend derived from ledger rows. |
| Savings goal edit and contribution | Automated | `FinanceServiceIntegrationTests` verifies progress updates without creating an expense transaction. |
| Savings goal concurrent edit protection | Automated | `FinanceServiceIntegrationTests` contributes after a read and verifies the stale full edit is rejected instead of replacing the new balance. |
| Recurring charge update and payment safety | Automated | `FinanceServiceIntegrationTests` verifies mandatory optimistic versions, stale edit rejection after payment, list-derived `expectedDueOn + version`, duplicate retry with exactly one expense, user-zone future-date rejection, and atomic payment/advance. |
| Recurring charge billing anchors | Automated | `FinanceServiceIntegrationTests` verifies month-end and yearly leap-day anchors recover after short periods and survive metadata-only updates. |
| Balance check-in and reconciliation | Automated | `FinanceServiceIntegrationTests` covers an explicit bank/cash breakdown for cash income, derived totals, first baseline, positive/negative discrepancies, ledger math, immutable adjustment rows, latest-only undo, and removal of the generated adjustment. |
| Category correction memory | Automated | `FinanceServiceIntegrationTests` verifies only real category changes persist and recent normalized examples are returned. |
| Finance insights and recurring suggestions | Automated | `FinanceServiceIntegrationTests` covers monthly/category/daily buckets plus stable-interval detection and exclusion of tracked, subscription, and reconciliation rows. |
| Multi-item expense extraction and receipt draft | Automated | `apps/api/src/test/java/com/northstar/api/capture/CaptureServiceIntegrationTests.java` uses a mocked ChatModel through the real structured-output path. |
| Banking SMS and correction prompting | Automated | `CaptureServiceIntegrationTests` asserts `GD`/`SD`, timestamp, multi-message examples, and recent user correction mappings are present in the extraction prompt. |
| Weekly ordinary vs one-off review facts | Automated | `apps/api/src/test/java/com/northstar/api/alignment/AlignmentServiceIntegrationTests.java` checks the persisted review note. |
| OpenAPI required fields and generated web types | Static | Regenerate `contracts/openapi.json`, run `pnpm -C web gen:api`, then `pnpm -C web typecheck`. |
| Finance page and Capture expense controls | Runtime verified | Playwright walkthroughs on 2026-07-10 and 2026-07-11 covered Finance at 1440x900 and 390x844, budget/goal flows, subscriptions, Insights charts/heatmap, CSV download, reconciliation dialog, mocked recurring prefill, and confirmed Capture controls; browser console was clean. |
| Planning assistant and MCP calls | Automated | `McpServerIntegrationTests` verifies discovery plus budget, savings-goal, and subscription write/read round trips over streamable HTTP; update/payment tool descriptions require list-derived version and due identity. |
| Live model Vietnamese extraction quality | Gap | Requires an opt-in provider-backed evaluation set; normal tests do not spend tokens. |
