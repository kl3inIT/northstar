# Note Project Linking Plan

## Implementation

- Add Flyway migration `V18__note_project.sql` with nullable `note.project_id`
  referencing `project(id)` using `ON DELETE SET NULL`.
- Add `projectId` to `Note`, `NoteSummary`, and `NoteDetail`.
- Extend `NoteService` create/update paths to accept and validate optional
  `projectId`, while keeping old overloads for existing callers.
- Add `NoteService.listByProject(UUID)` and `GET /api/notes/by-project`.
- Add `projectId` to note create/update request records.
- Regenerate the OpenAPI contract and web API types.
- Add web note editor project selector, note metadata project display, and
  project detail linked-notes panel.

## Verification

- API note integration test covers create/update/detach/list-by-project and
  invalid project rejection.
- `./gradlew --no-daemon compileJava`
- `./gradlew :core:test`
- `pnpm -C web typecheck`
- Browser smoke if app servers are started.

## Consolidation

- Update `docs/specs/domains/knowledge-base.md`.
- Update `docs/specs/domains/planning.md`.
- Update `docs/tests/domains/knowledge-base.md`.
- Update `docs/tests/domains/planning.md`.
