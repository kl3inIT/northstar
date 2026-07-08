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
| Project linked notes rail | Partial | Backend service test covers note membership; project detail UI now lists project notes and creates linked notes, but browser coverage is still needed. |
| Create task filed under a project | Gap | `TaskService.create` overload + `TaskRequest.projectId` + `create_task` tool param are unverified; needs an API/tool test asserting the new task carries `projectId` and rejects an unknown project. |
| Project detail board (Backlog/Planned/Done) | Gap | Column derivation from `(status, plannedDate)` and drag→setPlanned/setDone are UI-only; needs browser coverage. |
