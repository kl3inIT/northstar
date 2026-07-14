# Temporary Artifact Store Plan

Status: completed on 2026-07-14.

Execute in order. End each block with the listed local gate before moving on.

## Block A - Contract and bounded provider

- Add the open `core.artifact` module with validated immutable scope, metadata,
  content, write request, stats, and `TemporaryArtifactStore` port.
- Add `apps/api` Caffeine configuration and conditional default provider.
- Configure 30-minute TTL, 16 MiB per artifact, 64 MiB total weight, and 100
  effective entries in `application.yml`.
- Test validation, defensive copies, scope isolation, expiry with a fake ticker,
  weighted eviction, session cleanup, and concurrent consume.

Gate:

```powershell
.\gradlew.bat --no-daemon compileJava compileTestJava :core:test :apps:api:test --tests "com.northstar.api.artifact.*"
```

## Block B - Vocabulary enrichment migration

- Replace raw `GeneratedImage` and `SpeechAudio` job fields with scoped artifact
  references.
- Return typed reference metadata/URLs from job views; remove Base64 fields.
- Add the job-scoped authenticated binary endpoint with safe response headers.
- Keep late provider results from publishing after discard/expiry; clean partial
  artifacts on generation failure.
- Persist on Apply using peeked content and delete artifacts only after commit.
- Expand service/controller tests for reference-only views and lifecycle paths.

Gate:

```powershell
.\gradlew.bat --no-daemon compileJava compileTestJava :core:test :apps:api:test --tests "com.northstar.api.study.VocabEnrichmentJobServiceTests"
```

## Block C - Contract and web

- Regenerate `contracts/openapi.json` and the web client after the API shape
  changes; never hand-edit generated client files.
- Render the image/audio preview through reference URLs.
- Add only focused deterministic coverage for URL/reference presentation if the
  existing test boundary can express the regression cheaply.

Gate:

```powershell
pnpm -C web gen:api
pnpm -C web typecheck
pnpm -C web test
```

## Block D - Consolidation and release gates

- Update the Study spec and test matrix.
- Add the accepted architecture decision separating temporary artifacts from
  exact caches and durable attachments.
- Mark the roadmap item delivered and move this increment to `completed/`.
- Remove the temporary extracted dependency sources under `.tmp/`.

Gates:

```powershell
.\gradlew.bat --no-daemon compileJava compileTestJava :core:test
.\gradlew.bat --no-daemon clean test
pnpm -C web typecheck
pnpm -C web test
pnpm -C web build
git diff --check
```

Commit the coherent increment with a conventional commit and push only after
all required gates are green.
