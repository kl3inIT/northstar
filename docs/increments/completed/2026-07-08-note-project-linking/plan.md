# Note Project Linking Plan

## Implementation

- [x] Add Flyway migration `V18__note_project.sql` with nullable `note.project_id`
  referencing `project(id)` using `ON DELETE SET NULL`.
- [x] Add `projectId` to `Note`, `NoteSummary`, and `NoteDetail`.
- [x] Extend `NoteService` create/update paths to accept and validate optional
  `projectId`, while keeping old overloads for existing callers.
- [x] Add `NoteService.listByProject(UUID)` and `GET /api/notes/by-project`.
- [x] Add `projectId` to note create/update request records.
- [x] Regenerate the OpenAPI contract and web API types.
- [x] Add web note editor project selector, note metadata project display, and
  project detail linked-notes panel.

## Verification

- [x] API note integration test covers create/update/detach/list-by-project and
  invalid project rejection.
- [x] `./gradlew --no-daemon compileJava`
- [x] `./gradlew :core:test`
- [x] `pnpm -C web typecheck`
- [x] Record the remaining project-notes browser regression gap in the durable
  planning test matrix.

## Consolidation

- [x] Update `docs/specs/domains/knowledge-base.md`.
- [x] Update `docs/specs/domains/planning.md`.
- [x] Update `docs/tests/domains/knowledge-base.md`.
- [x] Update `docs/tests/domains/planning.md`.
