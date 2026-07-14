# Assistant Common File Attachments Plan

Status: active.

Execute in order. End each block with the listed local gate before moving on.

## Block A - Safe types and worker preparation

- Add one shared attachment type policy for the explicit common-file allowlist.
- Add the Spring AI PDF reader and preserve real page metadata.
- Add the search-owned attachment index-state migration and API model.
- Update worker indexing to publish processing, ready, unsupported, and safe
  failed states without moving parsing onto API request threads.
- Add focused policy, extraction, and state tests.

Gate:

```powershell
.\gradlew.bat --no-daemon compileJava compileTestJava :core:test
```

## Block B - Scoped Assistant document context

- Add bounded attachment-only retrieval in `SearchService`.
- Add batch preparation-status API and validate mixed attachments at chat time.
- Inject untrusted derived excerpts into the per-turn system prompt, keep raw
  bytes/context out of stored chat history, and emit structured file sources.
- Cover ready/not-ready/unknown/mixed turns and attachment isolation.

Gate:

```powershell
.\gradlew.bat --no-daemon compileJava compileTestJava :core:test :apps:api:test --tests "com.northstar.api.assistant.*" --tests "com.northstar.api.attachment.*"
```

## Block C - Composer UX and generated contract

- Regenerate `contracts/openapi.json` and the Hey API client after the API
  changes; do not hand-edit generated client files.
- Accept and render generic file tiles, keep the image-specific size rule, and
  upload through the existing immutable vault.
- Poll document preparation with visible Preparing/Ready/Failed state; preserve
  the draft on failure and dispatch once when all documents are ready.
- Add focused Vitest and Playwright coverage at the existing colocated/test
  boundaries.

Gate:

```powershell
pnpm -C web gen:api
pnpm -C web typecheck
pnpm -C web test
```

## Block D - Runtime proof and consolidation

- Run the focused Chromium attachment flow against local API/worker/web.
- Update specs, test matrices, roadmap, and add the accepted architecture
  decision; move this increment to `completed/`.
- Append verified behavior to the Northstar App Behavior note.

Gates:

```powershell
.\gradlew.bat --no-daemon compileJava compileTestJava :core:test
.\gradlew.bat --no-daemon clean test
pnpm -C web typecheck
pnpm -C web test
pnpm -C web build
git diff --check
```

Commit each coherent block in conventional-commit style. Do not push unless the
user asks.
