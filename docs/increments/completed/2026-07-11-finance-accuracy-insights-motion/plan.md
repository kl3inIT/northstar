# Finance Accuracy, Insights, And Motion Plan

- [x] Add V25 persistence for balance check-ins and category corrections.
- [x] Add Finance domain records, repositories, validation, aggregation, and
  recurring detection.
- [x] Expose reconciliation, insights, and recurring suggestion REST contracts.
- [x] Feed recent corrections and banking SMS examples into Capture extraction.
- [x] Add integration tests for every new domain rule.
- [x] Regenerate OpenAPI and the web client.
- [x] Install Motion and Recharts, then add shared motion and chart components.
- [x] Add Finance reconciliation, recurring suggestion, CSV, and Insights UI.
- [x] Run backend and frontend verification plus Playwright walkthroughs.
- [x] Consolidate specs, test matrix, decision record, architecture, and roadmap;
  then move this increment to completed.

## Completion Evidence

- `./gradlew.bat --no-daemon clean test` passed on 2026-07-11 across API,
  core, MCP, and worker.
- `pnpm -C web typecheck`, `pnpm -C web lint`, and `pnpm -C web build` passed;
  lint reports only the pre-existing calendar/router/CodeMirror/Kibo warnings.
- Playwright covered all four Finance tabs at 390x844 with zero horizontal
  overflow, desktop Finance, CSV download, reconciliation dialog, recurring
  subscription prefill, and a clean console.
- A completed 3/3 assistant workflow hydrated expanded on mobile and remained
  explicitly collapsible by its header.
