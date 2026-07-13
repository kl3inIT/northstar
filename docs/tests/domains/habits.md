# Habits Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| Schedule inclusion and effective history | Automated | `core/src/test/java/com/northstar/core/habit/HabitScheduleTests.java` pins weekday masks, weekly targets, effective versions, and invalid schedules. |
| CRUD, timezone Today, check-in replacement/clear | Automated | `apps/api/src/test/java/com/northstar/api/habit/HabitServiceIntegrationTests.java` uses real PostgreSQL/Flyway/JPA and the REST/service boundary. |
| Pause neutrality and archive/restore | Automated | `HabitServiceIntegrationTests` verifies paused opportunities are excluded and lifecycle changes retain evidence. |
| Consistency and streak derivation | Automated | Core/API tests pin expected/completed/excused rates and scheduled-day streak behavior. |
| Assistant/MCP parameter mapping | Automated | `core/src/test/java/com/northstar/core/assistant/HabitToolsTests.java` covers defaults, weekly targets, dates, statuses, and validation. |
| Weekly review habit facts | Automated | `AlignmentServiceIntegrationTests.weeklyReviewCarriesHabitConsistencyWithoutStreakPressure` verifies facts enter the durable review and omit streak pressure. |
| Typed web API and pure view helpers | Automated | Generated OpenAPI client plus `web/test/habit-utils.test.mjs`; `pnpm -C web test`, typecheck, lint, and production build pass. |
| Desktop Today/create/check-in | Browser | Playwright at 1707x876 created a daily habit, checked it in, and verified the dense row and 100% derived state. The fixture was removed afterward by its exact UUID. |
| Compact mobile Today/Insights | Browser | Playwright at 390x844 verified no page-level horizontal overflow, full-width check action, seven-day history, responsive editor, contribution-calendar scrolling, and newest-date positioning. |
| Pause/resume menu | Browser | Playwright verified the label and available action change in place, then restored the test habit before fixture cleanup. |
| Native mobile Habit surface | Gap | The Flutter app does not expose Habit V1 yet. |
