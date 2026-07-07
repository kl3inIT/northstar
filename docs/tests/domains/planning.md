# Planning Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| Task service integration | Automated | `apps/api/src/test/java/com/northstar/api/task/TaskServiceIntegrationTests.java` |
| Calendar event integration | Automated | `apps/api/src/test/java/com/northstar/api/calendar/CalendarEventServiceIntegrationTests.java` |
| Discipline delete contract | Automated | `apps/api/src/test/java/com/northstar/api/ApiErrorContractTests.java` covers empty delete and linked-work conflict. |
| Recurrence rule logic | Automated | `core/src/test/java/com/northstar/core/calendar/RecurrenceRuleTests.java` |
| Discipline/project UI flows | Partial | UI supports discipline create/edit/delete, but still needs browser coverage for these screens. |
| Project milestone edge cases | Gap | Needs focused service/API tests as project behavior grows. |
