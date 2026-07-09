# Knowledge Base Test Matrix

Reusable testing mechanics live in
[../../guidelines/testing-harness.md](../../guidelines/testing-harness.md).

| Behavior | Coverage | Notes |
| --- | --- | --- |
| Note service persistence and API behavior | Automated | `apps/api/src/test/java/com/northstar/api/note/NoteServiceIntegrationTests.java` |
| Note primary project link | Automated | `NoteServiceIntegrationTests` covers create with project, move to another project, detach, list by project, old update preserving project, and rejecting unknown projects. |
| Lexical note search | Automated | `NoteServiceIntegrationTests` covers title/body search, archived exclusion, snippets, Vietnamese tokens, and title typo fallback. |
| Search REST endpoint | Automated | `apps/api/src/test/java/com/northstar/api/note/NoteControllerIntegrationTests.java` covers `/api/notes/search` JSON results for Vietnamese lexical search and title typo fallback. |
| RRF scoring primitive | Automated | `core/src/test/java/com/northstar/core/search/SearchRankingTests.java` covers the rank contribution formula and invalid rank handling. |
| Duplicate title heading suppression | Static/UI behavior | Implemented in `web/src/components/markdown-body.tsx`; needs browser regression coverage when note UI tests are added. |
| Mermaid note rendering | Static/UI behavior | Implemented through the Markdown renderer; needs browser regression coverage when note UI tests are added. |
| Modulith boundaries | Automated | `core/src/test/java/com/northstar/core/ModulithVerificationTests.java` |
| Attachment and semantic indexing behavior | Gap | Needs focused integration coverage for vector-store indexing, RRF overlap behavior, and attachment hits as indexing evolves. |
| Live notes UI workflow | Gap | Should be covered by Playwright when the next UI increment touches notes. |
