# Knowledge Base Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| Note service persistence and API behavior | Automated | `apps/api/src/test/java/com/northstar/api/note/NoteServiceIntegrationTests.java` |
| Note primary project link | Automated | `NoteServiceIntegrationTests` covers create with project, move to another project, detach, list by project, old update preserving project, and rejecting unknown projects. |
| Duplicate title heading suppression | Static/UI behavior | Implemented in `web/src/components/markdown-body.tsx`; needs browser regression coverage when note UI tests are added. |
| Mermaid note rendering | Static/UI behavior | Implemented through the Markdown renderer; needs browser regression coverage when note UI tests are added. |
| Modulith boundaries | Automated | `core/src/test/java/com/northstar/core/ModulithVerificationTests.java` |
| Attachment and search indexing behavior | Gap | Needs focused integration coverage as indexing evolves. |
| Live notes UI workflow | Gap | Should be covered by Playwright when the next UI increment touches notes. |
