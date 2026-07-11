# Automations Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| Migration/JPA/scheduler schema compatibility | Automated | `NorthstarWorkerContextLoadTests` runs Flyway through V27 on PostgreSQL and boots the worker with `ddl-auto: validate` plus db-scheduler beans. |
| Trigger validation and timezone-aware cron compilation | Automated | API invalid-trigger cases plus `AutomationSchedulerCoordinatorTests` verify weekday ordering, six-field cron, and IANA timezone. |
| Definition projection state | Automated | `AutomationSchedulerCoordinatorTests` verifies an unsynced disabled definition is reconciled and its exact schedule version acknowledged. |
| Definition CRUD, optimistic version, types, run-now, and history | Automated | `AutomationControllerIntegrationTests` uses PostgreSQL and MockMvc, including invalid workflow type/trigger and secret-boundary assertions. |
| Morning Brief validation, source deduplication, deterministic Markdown, and Staging output | Automated | `MorningBriefHandlerTests` uses fake research/note services and tracking-URL variants. |
| Same automation/day output idempotency | Automated | `MorningBriefHandlerTests` verifies a rerun updates the existing named daily note instead of creating another. |
| Generated API trust boundary | Automated | OpenAPI is committed; Hey API emits DTO/SDK/Zod clients and `automation-api.ts` parses list/history responses before UI use. |
| Settings create/run/history/delete flow | Runtime | Playwright on 2026-07-11 exercised the real API at desktop and 390x844. It caught and verified fixes for nullable contract parsing and history-cache invalidation after deletion. |
| Settings layout and accessibility | Runtime | Desktop/mobile screenshots and accessibility snapshots verify labeled controls, independent dialog scrolling, stable action layout, and no new console errors in the final request flow. |
| Live paid Morning Brief execution | Deferred verification | Search adapters are covered with fakes and live Web Research was verified in its own increment. This increment does not spend a real provider call solely to create test content. |
| Two live worker processes contending for one due occurrence | Residual gap | Cluster claiming is delegated to db-scheduler and the database primary key; a future deployment smoke test should start two worker containers against one disposable database. |
