# Knowledge Base Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| Note service persistence and API behavior | Automated | `apps/api/src/test/java/com/northstar/api/note/NoteServiceIntegrationTests.java` |
| Modulith boundaries | Automated | `core/src/test/java/com/northstar/core/ModulithVerificationTests.java` |
| Attachment and search indexing behavior | Gap | Needs focused integration coverage as indexing evolves. |
| Live notes UI workflow | Gap | Should be covered by Playwright when the next UI increment touches notes. |
