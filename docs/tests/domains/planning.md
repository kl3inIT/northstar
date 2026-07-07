# Planning Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| Task service integration | Automated | `apps/api/src/test/java/com/northstar/api/task/TaskServiceIntegrationTests.java` |
| Calendar event integration | Automated | `apps/api/src/test/java/com/northstar/api/calendar/CalendarEventServiceIntegrationTests.java` |
| Recurrence rule logic | Automated | `core/src/test/java/com/northstar/core/calendar/RecurrenceRuleTests.java` |
| Discipline/project UI flows | Gap | Needs browser coverage when these screens change. |
| Project milestone edge cases | Gap | Needs focused service/API tests as project behavior grows. |
